package io.codehunters.messaging;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port the dispatcher, the purge job and operators use on stored outbox messages: claim,
 * mark, update, purge, count and requeue. Applications write through {@link OutboxWriter} instead.
 */
public interface OutboxDispatchStore {

  /**
   * Atomically transitions up to {@code batchSize} eligible rows of the request's origin to
   * IN_FLIGHT and returns them in insertion order. Eligible rows are PENDING with {@code
   * next_attempt_at <= now} OR IN_FLIGHT whose {@code claimed_at} is older than {@code claimTtl}
   * (stale-claim recovery). A reclaimed row is charged one attempt only when its previous owner had
   * started sending it (see {@link #markSendStarted}); every claim clears that mark.
   *
   * @param request the origin, claimer, batch size and claim TTL
   * @return the claimed messages, with their bodies serialized as {@link String}
   */
  List<OutboxMessage<String>> claim(ClaimRequest request);

  /**
   * Records that the claim owner is about to hand {@code outboxMessage} to the broker. Called right
   * before each publish, so a later reclaim can tell sent rows from rows that never left the batch.
   * Fenced by the message's claim marker: when another dispatcher reclaimed the row in the
   * meantime, nothing is written and the caller must not publish it.
   *
   * @param outboxMessage the claimed message about to be published
   * @return the marked message while this caller still holds the claim, otherwise empty
   */
  Optional<OutboxMessage<String>> markSendStarted(OutboxMessage<String> outboxMessage);

  /**
   * Writes an outbox message's status, attempt counter, error and timestamps. Fenced by its claim
   * marker when it has one. Ending the claim (any status but IN_FLIGHT) clears the claim columns
   * and the send mark.
   *
   * @param outboxMessage the message with its new state
   */
  void update(OutboxMessage<String> outboxMessage);

  /**
   * Deletes up to {@code batchSize} PUBLISHED rows of this store's origin whose {@code
   * published_at} is older than {@code cutoff}. PENDING, IN_FLIGHT and FAILED rows are never
   * deleted. Callers loop while the result equals {@code batchSize}.
   *
   * @param cutoff the instant before which published rows are purged
   * @param batchSize the maximum number of rows deleted by this call
   * @return the number of rows deleted
   */
  int purgePublished(Instant cutoff, int batchSize);

  /**
   * Counts the FAILED rows of this store's origin.
   *
   * @return the number of FAILED rows
   */
  long countFailed();

  /**
   * Puts the listed FAILED rows of this store's origin back to PENDING, due now, with zero attempts
   * and no error, claim or send mark, so the dispatcher publishes them again and their keys are no
   * longer blocked. Rows of other origins or in any other status are left untouched. When at least
   * one row is requeued, a dispatch is requested once the surrounding transaction commits.
   *
   * @param ids the ids of the rows to requeue; an empty collection requeues nothing
   * @return the number of rows requeued
   */
  int requeue(Collection<UUID> ids);

  /**
   * Puts every FAILED row of this store's origin whose message key is {@code key}, in any topic,
   * back to PENDING as {@link #requeue} does.
   *
   * @param key the message key whose failed rows are requeued; never null
   * @return the number of rows requeued
   */
  int requeueFailed(String key);
}
