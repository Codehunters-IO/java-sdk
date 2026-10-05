package io.codehunters.messaging.infrastructure.drivenadapters.jdbc;

/**
 * PostgreSQL claim: one statement that locks the candidate rows, skips rows locked by a concurrent
 * claimer, marks them {@code IN_FLIGHT} and returns them. Only the candidate scan is locked; the
 * per-key ordering subquery is a plain snapshot read, so an earlier same-key row being claimed by
 * another dispatcher still blocks later rows.
 */
public final class PostgresOutboxDialect implements OutboxDialect {

  private static final String CLAIM_SQL =
      """
      WITH candidates AS (
        SELECT o.id
        FROM outbox_message o
        WHERE o.origin = :origin
          AND ((o.status = 'PENDING' AND (o.next_attempt_at IS NULL OR o.next_attempt_at <= :now))
            OR (o.status = 'IN_FLIGHT' AND o.claimed_at <= :ttlBoundary))
          AND NOT EXISTS (
            SELECT 1 FROM outbox_message p
            WHERE p.origin = o.origin AND p.topic = o.topic AND p.message_key = o.message_key
              AND p.status IN ('PENDING', 'IN_FLIGHT', 'FAILED')
              AND p.seq < o.seq)
        ORDER BY o.seq
        LIMIT :limit
        FOR UPDATE OF o SKIP LOCKED
      )
      UPDATE outbox_message m
      SET status = 'IN_FLIGHT', claimed_at = :now, claimed_by = :claimedBy
      FROM candidates c
      WHERE m.id = c.id
      RETURNING m.id, m.origin, m.routing_key, m.topic, m.message_key, m.payload, m.status,
          m.attempts, m.error, m.occurred_at, m.next_attempt_at, m.published_at, m.claimed_at,
          m.claimed_by, m.seq
      """;

  private static final String PURGE_PUBLISHED_SQL =
      """
      DELETE FROM outbox_message
      WHERE id IN (
          SELECT id FROM outbox_message
          WHERE origin = :origin AND status = 'PUBLISHED' AND published_at < :cutoff
          ORDER BY published_at
          LIMIT :limit)
      """;

  /** Creates the PostgreSQL dialect. */
  public PostgresOutboxDialect() {
    // stateless
  }

  @Override
  public String name() {
    return "postgresql";
  }

  @Override
  public String claimSql() {
    return CLAIM_SQL;
  }

  @Override
  public boolean claimReturnsRows() {
    return true;
  }

  @Override
  public String purgePublishedSql() {
    return PURGE_PUBLISHED_SQL;
  }
}
