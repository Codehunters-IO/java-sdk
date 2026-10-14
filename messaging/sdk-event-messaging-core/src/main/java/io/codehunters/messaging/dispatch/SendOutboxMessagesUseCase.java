package io.codehunters.messaging.dispatch;

import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxDispatchStore;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.OutboxStatus;
import io.codehunters.messaging.SendOutboxMessagesPort;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use case that claims a batch of pending outbox messages, publishes each via the {@link
 * MessagePublisherPort} and writes the outcome back to the {@link OutboxDispatchStore}. Failed
 * publications are retried with the {@link RetryPolicy}'s exponential backoff until its {@code
 * maxAttempts} is reached, after which the message is marked {@code FAILED}.
 *
 * <p>Two guards protect against duplicates and poison messages. A claimed message whose attempts
 * already reached {@code maxAttempts} (reclaimed after dispatcher crashes) is marked {@code FAILED}
 * without being published. Publishing stops once half the claim TTL has elapsed since the claim;
 * the remaining messages are released back to {@code PENDING} before their claim can go stale, so
 * no other instance reclaims a message this one may still be sending.
 *
 * <p>Each message is marked as started right before it is published. A reclaim after a crash
 * charges an attempt only to marked rows, and a message whose mark is refused (its claim was taken
 * over) is neither published nor updated. A mark that fails, for example on a transient database
 * error, skips only that message.
 *
 * <p>{@link #stop()} ends dispatching for good: the message being published finishes, the rest of
 * the batch is released back to {@code PENDING} unsent, and later runs claim nothing. A shutdown
 * then waits for at most one send instead of a whole batch, and leaves no claims to expire.
 *
 * <p>A publish ended by an interrupt, as when the dispatch executor shuts down, is not charged as a
 * failed attempt: the row is released back to {@code PENDING} unchanged, the thread's interrupt
 * flag is kept, and the rest of the batch is released unsent. A write-back that fails is logged by
 * exception class and leaves the row {@code IN_FLIGHT} for a reclaim after the TTL; the later rows
 * of its key are released and the other keys go on.
 *
 * <p>A claim may hold several rows of one {@code (topic, key)}, in insertion order. Once one of
 * them is not published (failed, given up, refused or released), every later row of that key in the
 * batch is released unsent, so a key is never delivered out of order; rows without a key are
 * independent.
 */
public class SendOutboxMessagesUseCase implements SendOutboxMessagesPort {

  static final String MAX_ATTEMPTS_EXCEEDED = "max-attempts-exceeded";

  private static final Logger log = LoggerFactory.getLogger(SendOutboxMessagesUseCase.class);

  private final OutboxDispatchStore store;
  private final MessagePublisherPort publisher;
  private final DispatchSettings settings;
  private final Duration publishBudget;
  private volatile boolean stopped;

  /**
   * Creates the use case.
   *
   * @param store the outbox store the messages are claimed from and written back to
   * @param publisher the message publisher
   * @param settings the claim, retry policy and clock
   */
  public SendOutboxMessagesUseCase(
      OutboxDispatchStore store, MessagePublisherPort publisher, DispatchSettings settings) {
    this.store = store;
    this.publisher = publisher;
    this.settings = settings;
    this.publishBudget = settings.claim().claimTtl().dividedBy(2);
  }

  @Override
  public boolean execute() {
    if (stopped) {
      return false;
    }
    Batch batch = new Batch(settings.clock().instant(), new HashSet<>());
    List<OutboxMessage<String>> claimed = store.claim(settings.claim());
    for (OutboxMessage<String> message : claimed) {
      dispatchInKeyOrder(message, batch);
    }
    log.debug("Outbox dispatch processed {} messages", claimed.size());
    return claimed.size() >= settings.claim().batchSize();
  }

  private void dispatchInKeyOrder(OutboxMessage<String> message, Batch batch) {
    Optional<OrderingKey> key = OrderingKey.of(message);
    if (key.filter(batch.stoppedKeys()::contains).isPresent()) {
      write(releaseBehindKey(message));
      return;
    }
    Optional<OutboxMessage<String>> outcome = dispatch(message, batch.claimStart());
    boolean published = outcome.filter(SendOutboxMessagesUseCase::isPublished).isPresent();
    boolean written = outcome.map(this::write).orElse(false);
    if (!published || !written) {
      key.ifPresent(batch.stoppedKeys()::add);
    }
  }

  // A failed write leaves the row IN_FLIGHT under this claim: later rows of its key are released
  // and the row itself is reclaimed after the TTL, so the batch goes on with the other keys.
  private boolean write(OutboxMessage<String> message) {
    try {
      store.update(message);
      return true;
    } catch (RuntimeException e) {
      log.warn(
          "Outbox message id={} left in flight: writing status {} failed with {}",
          message.id(),
          message.status(),
          e.getClass().getName());
      return false;
    }
  }

  private static boolean isPublished(OutboxMessage<String> message) {
    return message.status() == OutboxStatus.PUBLISHED;
  }

  private OutboxMessage<String> releaseBehindKey(OutboxMessage<String> message) {
    log.debug(
        "Releasing outbox message id={}: an earlier message of its key was not published",
        message.id());
    return message.released(settings.clock().instant());
  }

  // Returns the message's new state, or empty when the claim was lost and nothing may be written.
  private Optional<OutboxMessage<String>> dispatch(
      OutboxMessage<String> message, Instant claimStart) {
    if (stopped || isBudgetExhausted(claimStart) || Thread.currentThread().isInterrupted()) {
      return Optional.of(release(message));
    }
    if (settings.retry().isExhausted(message.attempts())) {
      return Optional.of(markExhausted(message));
    }
    return markSendStarted(message).map(this::publish);
  }

  // A row whose mark is refused or fails stays IN_FLIGHT and unmarked: a reclaim after the TTL
  // picks it up without charging an attempt, and the rest of the batch goes on.
  private Optional<OutboxMessage<String>> markSendStarted(OutboxMessage<String> message) {
    try {
      Optional<OutboxMessage<String>> marked = store.markSendStarted(message);
      if (marked.isEmpty()) {
        log.warn("Outbox message id={} skipped: its claim was taken over", message.id());
      }
      return marked;
    } catch (RuntimeException e) {
      log.warn(
          "Outbox message id={} skipped: marking its send failed with {}",
          message.id(),
          e.getClass().getName());
      return Optional.empty();
    }
  }

  /**
   * Stops dispatching, typically when the application shuts down. A run in progress finishes the
   * message it is publishing and releases the rest of its batch; later runs claim nothing. Cannot
   * be undone.
   */
  public void stop() {
    stopped = true;
  }

  private boolean isBudgetExhausted(Instant claimStart) {
    Duration elapsed = Duration.between(claimStart, settings.clock().instant());
    return elapsed.compareTo(publishBudget) >= 0;
  }

  private OutboxMessage<String> release(OutboxMessage<String> message) {
    log.debug(
        "Releasing outbox message id={}: claim time budget spent or shutting down", message.id());
    return message.released(settings.clock().instant());
  }

  private OutboxMessage<String> markExhausted(OutboxMessage<String> message) {
    log.warn(
        "Outbox message id={} reclaimed after {} attempts, marking FAILED",
        message.id(),
        message.attempts());
    return message.failed(MAX_ATTEMPTS_EXCEEDED);
  }

  private OutboxMessage<String> publish(OutboxMessage<String> message) {
    try {
      publisher.publish(message);
      return message.published(settings.clock().instant());
    } catch (RuntimeException e) {
      return isInterruption(e) ? interrupted(message) : failed(message, e);
    }
  }

  private OutboxMessage<String> failed(OutboxMessage<String> message, RuntimeException e) {
    log.error("Failed to publish outbox message id={}: {}", message.id(), e.getClass().getName());
    return retryOrFail(message.attemptFailed(e.getClass().getName()));
  }

  // Shutdown, not the broker, ended the publish: the row goes back unchanged, and the flag is set
  // again (a client may have swallowed it) so the rest of the batch is released too.
  private OutboxMessage<String> interrupted(OutboxMessage<String> message) {
    Thread.currentThread().interrupt();
    log.info("Outbox message id={} released: its publish was interrupted", message.id());
    return message.released(settings.clock().instant());
  }

  private static boolean isInterruption(Throwable failure) {
    if (Thread.currentThread().isInterrupted()) {
      return true;
    }
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof InterruptedException) {
        return true;
      }
    }
    return false;
  }

  private OutboxMessage<String> retryOrFail(OutboxMessage<String> failed) {
    if (settings.retry().isExhausted(failed.attempts())) {
      return failed.failed(failed.error());
    }
    Duration backoff = settings.retry().backoff(failed.attempts());
    return failed.retryAt(settings.clock().instant().plus(backoff));
  }

  // The claim start, for the publish budget, and the keys whose remaining rows are released.
  private record Batch(Instant claimStart, Set<OrderingKey> stoppedKeys) {}

  // Rows are ordered per (topic, key); rows without a key are not ordered.
  private record OrderingKey(String topic, String key) {

    static Optional<OrderingKey> of(OutboxMessage<String> message) {
      return Optional.ofNullable(message.key()).map(key -> new OrderingKey(message.topic(), key));
    }
  }
}
