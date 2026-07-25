package io.codehunters.messaging.application.port;

import io.codehunters.messaging.domain.model.OutboxMessage;
import java.time.Duration;
import java.util.List;

/** Outbound port for persisting and dispatching outbox messages. */
public interface OutboxRepositoryPort {

  /**
   * Persists a new outbox message.
   *
   * @param outboxMessage the message to persist
   * @param <T> the message body type
   */
  <T> void save(OutboxMessage<T> outboxMessage);

  /**
   * Atomically transitions up to {@code batchSize} eligible rows scoped to {@code origin} to
   * IN_FLIGHT and returns them. Eligible rows are PENDING with {@code next_attempt_at <= now} OR
   * IN_FLIGHT whose {@code claimed_at} is older than {@code claimTtl} (stale-claim recovery).
   *
   * @param origin the origin of the messages to claim
   * @param batchSize the maximum number of messages to claim
   * @param claimedBy the identifier of the caller claiming the messages
   * @param claimTtl the time after which an in-flight claim is considered stale and reclaimable
   * @return the claimed messages, with their bodies serialized as {@link String}
   */
  List<OutboxMessage<String>> claimPendingMessages(
      String origin, int batchSize, String claimedBy, Duration claimTtl);

  /**
   * Updates an outbox message's status, attempt counter and next-attempt timestamp.
   *
   * @param outboxMessage the message with updated state
   */
  void update(OutboxMessage<String> outboxMessage);
}
