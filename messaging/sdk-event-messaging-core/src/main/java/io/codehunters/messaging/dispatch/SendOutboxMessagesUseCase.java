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
      store.update(releaseBehindKey(message));
      return;
    }
    Optional<OutboxMessage<String>> outcome = dispatch(message, batch.claimStart());
    outcome.ifPresent(store::update);
    if (outcome.filter(SendOutboxMessagesUseCase::isPublished).isEmpty()) {
      key.ifPresent(batch.stoppedKeys()::add);
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
    if (isBudgetExhausted(claimStart)) {
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

  private boolean isBudgetExhausted(Instant claimStart) {
    Duration elapsed = Duration.between(claimStart, settings.clock().instant());
    return elapsed.compareTo(publishBudget) >= 0;
  }

  private OutboxMessage<String> release(OutboxMessage<String> message) {
    log.debug("Releasing outbox message id={}: claim time budget spent", message.id());
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
      log.error("Failed to publish outbox message id={}: {}", message.id(), e.getClass().getName());
      return retryOrFail(message.attemptFailed(e.getClass().getName()));
    }
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
