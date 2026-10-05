package io.codehunters.messaging.application.usecase;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.codehunters.messaging.application.port.SendOutboxMessagesPort;
import io.codehunters.messaging.domain.model.OutboxMessage;
import io.codehunters.messaging.domain.model.OutboxStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case that claims a batch of pending outbox messages, publishes each via the {@link
 * MessagePublisherPort} and updates persistent state. Failed publications are retried with
 * exponential backoff until {@code maxAttempts} is reached, after which the message is marked
 * {@code FAILED}.
 *
 * <p>Two guards protect against duplicates and poison messages. A claimed message whose attempts
 * already reached {@code maxAttempts} (reclaimed after dispatcher crashes) is marked {@code FAILED}
 * without being published. Publishing stops once half the claim TTL has elapsed since the claim;
 * the remaining messages are released back to {@code PENDING} before their claim can go stale, so
 * no other instance reclaims a message this one may still be sending.
 */
@Slf4j
public class SendOutboxMessagesUseCase implements SendOutboxMessagesPort {

  static final String MAX_ATTEMPTS_EXCEEDED = "max-attempts-exceeded";

  private final OutboxRepositoryPort outboxRepositoryPort;
  private final MessagePublisherPort messagePublisherPort;
  private final int batchSize;
  private final int maxAttempts;
  private final String instanceId;
  private final String origin;
  private final Duration claimTtl;
  private final Duration publishBudget;
  private final long baseBackoffMs;
  private final long maxBackoffMs;
  private final Clock clock;

  /**
   * Creates the use case with a UTC system clock.
   *
   * @param outboxRepositoryPort the outbox repository
   * @param messagePublisherPort the message publisher
   * @param batchSize the maximum number of messages dispatched per call
   * @param maxAttempts the maximum number of publish attempts before marking a message FAILED
   * @param instanceId the identifier of this dispatcher instance (used as claim marker)
   * @param origin the origin scope used to claim messages
   * @param claimTtlMs the stale-claim TTL in milliseconds; publishing stops once half of it elapsed
   * @param baseBackoffMs the initial retry backoff in milliseconds
   * @param maxBackoffMs the maximum retry backoff in milliseconds
   */
  public SendOutboxMessagesUseCase(
      OutboxRepositoryPort outboxRepositoryPort,
      MessagePublisherPort messagePublisherPort,
      int batchSize,
      int maxAttempts,
      String instanceId,
      String origin,
      long claimTtlMs,
      long baseBackoffMs,
      long maxBackoffMs) {
    this(
        outboxRepositoryPort,
        messagePublisherPort,
        batchSize,
        maxAttempts,
        instanceId,
        origin,
        claimTtlMs,
        baseBackoffMs,
        maxBackoffMs,
        Clock.systemUTC());
  }

  /**
   * Creates the use case with an explicit clock for every timestamp it writes. This overload only
   * appends {@code clock} to the pre-existing public constructor, which services already consume;
   * replacing the long parameter list with a parameter object is a separate breaking change.
   *
   * @param outboxRepositoryPort the outbox repository
   * @param messagePublisherPort the message publisher
   * @param batchSize the maximum number of messages dispatched per call
   * @param maxAttempts the maximum number of publish attempts before marking a message FAILED
   * @param instanceId the identifier of this dispatcher instance (used as claim marker)
   * @param origin the origin scope used to claim messages
   * @param claimTtlMs the stale-claim TTL in milliseconds; publishing stops once half of it elapsed
   * @param baseBackoffMs the initial retry backoff in milliseconds
   * @param maxBackoffMs the maximum retry backoff in milliseconds
   * @param clock the clock for publish and retry timestamps (UTC expected)
   */
  public SendOutboxMessagesUseCase(
      OutboxRepositoryPort outboxRepositoryPort,
      MessagePublisherPort messagePublisherPort,
      int batchSize,
      int maxAttempts,
      String instanceId,
      String origin,
      long claimTtlMs,
      long baseBackoffMs,
      long maxBackoffMs,
      Clock clock) {
    this.outboxRepositoryPort = outboxRepositoryPort;
    this.messagePublisherPort = messagePublisherPort;
    this.batchSize = batchSize;
    this.maxAttempts = maxAttempts;
    this.instanceId = instanceId;
    this.origin = origin;
    this.claimTtl = Duration.ofMillis(claimTtlMs);
    this.publishBudget = claimTtl.dividedBy(2);
    this.baseBackoffMs = baseBackoffMs;
    this.maxBackoffMs = maxBackoffMs;
    this.clock = clock;
  }

  @Override
  public void execute() {
    log.debug("[START] - send outbox messages, instance={}, origin={}", instanceId, origin);
    Instant claimStart = clock.instant();
    List<OutboxMessage<String>> claimed =
        outboxRepositoryPort.claimPendingMessages(origin, batchSize, instanceId, claimTtl);

    for (OutboxMessage<String> message : claimed) {
      dispatch(message, claimStart);
      outboxRepositoryPort.update(message);
    }

    log.debug("[END] - send outbox messages, processed {} messages", claimed.size());
  }

  private void dispatch(OutboxMessage<String> message, Instant claimStart) {
    if (isBudgetExhausted(claimStart)) {
      release(message);
    } else if (message.getAttempts() >= maxAttempts) {
      markExhausted(message);
    } else {
      processMessage(message);
    }
  }

  private boolean isBudgetExhausted(Instant claimStart) {
    return Duration.between(claimStart, clock.instant()).compareTo(publishBudget) >= 0;
  }

  // The claim marker stays on the message: the repository fences the update with it and clears
  // the claim columns of any row that is no longer IN_FLIGHT.
  private void release(OutboxMessage<String> message) {
    log.debug("Releasing outbox message id={}: claim time budget spent", message.getId());
    message.setStatus(OutboxStatus.PENDING);
    message.setClaimedAt(null);
    message.setNextAttemptAt(LocalDateTime.now(clock));
  }

  private void markExhausted(OutboxMessage<String> message) {
    log.warn(
        "Outbox message id={} reclaimed after {} attempts, marking FAILED",
        message.getId(),
        message.getAttempts());
    message.setStatus(OutboxStatus.FAILED);
    message.setError(MAX_ATTEMPTS_EXCEEDED);
    message.setNextAttemptAt(null);
  }

  private void processMessage(OutboxMessage<String> message) {
    try {
      messagePublisherPort.publish(message);
      markPublished(message);
    } catch (Exception e) {
      log.error(
          "Failed to publish outbox message id={}: {}", message.getId(), e.getClass().getName());
      markRetryOrFailed(message, e);
    }
  }

  private void markPublished(OutboxMessage<String> message) {
    message.setStatus(OutboxStatus.PUBLISHED);
    message.setPublishedAt(LocalDateTime.now(clock));
    message.setError(null);
    message.setNextAttemptAt(null);
  }

  private void markRetryOrFailed(OutboxMessage<String> message, Exception e) {
    int attempts = message.getAttempts() + 1;
    message.setAttempts(attempts);
    message.setError(e.getClass().getName());
    if (attempts >= maxAttempts) {
      message.setStatus(OutboxStatus.FAILED);
      message.setNextAttemptAt(null);
    } else {
      message.setStatus(OutboxStatus.PENDING);
      message.setNextAttemptAt(
          LocalDateTime.now(clock).plusNanos(computeBackoffMs(attempts) * 1_000_000L));
    }
  }

  long computeBackoffMs(int attempts) {
    int shift = Math.min(attempts - 1, 30);
    long delay = baseBackoffMs << shift;
    if (delay <= 0 || delay > maxBackoffMs) {
      return maxBackoffMs;
    }
    return delay;
  }
}
