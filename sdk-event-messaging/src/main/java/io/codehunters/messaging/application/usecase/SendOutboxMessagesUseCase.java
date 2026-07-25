package io.codehunters.messaging.application.usecase;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.codehunters.messaging.application.port.SendOutboxMessagesPort;
import io.codehunters.messaging.domain.model.OutboxMessage;
import io.codehunters.messaging.domain.model.OutboxStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case that claims a batch of pending outbox messages, publishes each via the {@link
 * MessagePublisherPort} and updates persistent state. Failed publications are retried with
 * exponential backoff until {@code maxAttempts} is reached, after which the message is marked
 * {@code FAILED}.
 */
@Slf4j
public class SendOutboxMessagesUseCase implements SendOutboxMessagesPort {

  private final OutboxRepositoryPort outboxRepositoryPort;
  private final MessagePublisherPort messagePublisherPort;
  private final int batchSize;
  private final int maxAttempts;
  private final String instanceId;
  private final String origin;
  private final Duration claimTtl;
  private final long baseBackoffMs;
  private final long maxBackoffMs;

  /**
   * Creates the use case.
   *
   * @param outboxRepositoryPort the outbox repository
   * @param messagePublisherPort the message publisher
   * @param batchSize the maximum number of messages dispatched per call
   * @param maxAttempts the maximum number of publish attempts before marking a message FAILED
   * @param instanceId the identifier of this dispatcher instance (used as claim marker)
   * @param origin the origin scope used to claim messages
   * @param claimTtlMs the stale-claim TTL in milliseconds
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
    this.outboxRepositoryPort = outboxRepositoryPort;
    this.messagePublisherPort = messagePublisherPort;
    this.batchSize = batchSize;
    this.maxAttempts = maxAttempts;
    this.instanceId = instanceId;
    this.origin = origin;
    this.claimTtl = Duration.ofMillis(claimTtlMs);
    this.baseBackoffMs = baseBackoffMs;
    this.maxBackoffMs = maxBackoffMs;
  }

  @Override
  public void execute() {
    log.debug("[START] - send outbox messages, instance={}, origin={}", instanceId, origin);
    List<OutboxMessage<String>> claimed =
        outboxRepositoryPort.claimPendingMessages(origin, batchSize, instanceId, claimTtl);

    for (OutboxMessage<String> message : claimed) {
      processMessage(message);
      outboxRepositoryPort.update(message);
    }

    log.debug("[END] - send outbox messages, processed {} messages", claimed.size());
  }

  private void processMessage(OutboxMessage<String> message) {
    try {
      publish(message);
      markPublished(message);
    } catch (Exception e) {
      log.error("Failed to publish outbox message id={}: {}", message.getId(), e.getMessage());
      markRetryOrFailed(message, e);
    }
  }

  private void publish(OutboxMessage<String> message) {
    String routingKey = message.getRoutingKey();
    if (hasRoutingKey(routingKey)) {
      messagePublisherPort.publish(message.getTopic(), routingKey, message.getMessage());
    } else {
      messagePublisherPort.publish(message.getTopic(), message.getMessage());
    }
  }

  private boolean hasRoutingKey(String routingKey) {
    return routingKey != null && !routingKey.isBlank();
  }

  private void markPublished(OutboxMessage<String> message) {
    message.setStatus(OutboxStatus.PUBLISHED);
    message.setPublishedAt(LocalDateTime.now());
    message.setClaimedAt(null);
    message.setClaimedBy(null);
    message.setError(null);
    message.setNextAttemptAt(null);
  }

  private void markRetryOrFailed(OutboxMessage<String> message, Exception e) {
    int attempts = message.getAttempts() + 1;
    message.setAttempts(attempts);
    message.setError(e.getMessage());
    message.setClaimedAt(null);
    message.setClaimedBy(null);
    if (attempts >= maxAttempts) {
      message.setStatus(OutboxStatus.FAILED);
      message.setNextAttemptAt(null);
    } else {
      message.setStatus(OutboxStatus.PENDING);
      message.setNextAttemptAt(
          LocalDateTime.now().plusNanos(computeBackoffMs(attempts) * 1_000_000L));
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
