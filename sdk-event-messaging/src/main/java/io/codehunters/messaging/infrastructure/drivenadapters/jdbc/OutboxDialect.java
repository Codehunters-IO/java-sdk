package io.codehunters.messaging.infrastructure.drivenadapters.jdbc;

/**
 * Database-specific SQL for the outbox claim. Each dialect marks a batch of claimable rows as
 * {@code IN_FLIGHT} for one dispatcher instance. A row is claimable when it is {@code PENDING} and
 * due, or {@code IN_FLIGHT} with a claim older than the TTL boundary, and no earlier row (by {@code
 * seq}) with the same {@code (origin, topic, message_key)} is still {@code PENDING}, {@code
 * IN_FLIGHT} or {@code FAILED}.
 *
 * <p>The claim SQL binds only these named parameters: {@code :origin}, {@code :now}, {@code
 * :ttlBoundary}, {@code :limit} and {@code :claimedBy}. Rows returned by the claim come back in no
 * particular order; callers sort them by {@code seq}.
 */
public interface OutboxDialect {

  /**
   * Returns the configuration name of this dialect, as accepted by {@code outbox.claim.dialect}.
   *
   * @return the dialect name
   */
  String name();

  /**
   * Returns the parameterized claim statement.
   *
   * @return constant claim SQL using named parameters only
   */
  String claimSql();

  /**
   * Tells whether {@link #claimSql()} returns the claimed rows as a result set.
   *
   * @return {@code true} when the claim returns rows; {@code false} when the caller must select the
   *     claimed rows by {@code claimed_by} afterwards
   */
  boolean claimReturnsRows();
}
