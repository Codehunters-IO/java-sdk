package io.codehunters.messaging.jdbc;

/**
 * SQL Server claim: an updatable CTE over the candidate scan, locked with update/row locks that
 * skip rows held by a concurrent claimer, returning the claimed rows through {@code OUTPUT}. The
 * lock hint sits on the outer candidate scan only; the per-key ordering subquery carries no hint,
 * so an earlier same-key row being claimed elsewhere is still visible and blocks later rows.
 *
 * <p>Pinned by string tests and exercised against the SQL Server reference DDL on SQL Server 2022
 * (x86-64 hosts) or azure-sql-edge (arm64 hosts).
 */
public final class SqlServerOutboxDialect implements OutboxDialect {

  private static final String CLAIM_SQL =
      """
      WITH candidates AS (
        SELECT TOP (:limit) o.*
        FROM outbox_message o WITH (UPDLOCK, READPAST, ROWLOCK)
        WHERE o.origin = :origin
          AND ((o.status = 'PENDING' AND (o.next_attempt_at IS NULL OR o.next_attempt_at <= :now))
            OR (o.status = 'IN_FLIGHT' AND o.claimed_at <= :ttlBoundary))
          AND NOT EXISTS (
            SELECT 1 FROM outbox_message p
            WHERE p.origin = o.origin AND p.topic = o.topic AND p.message_key = o.message_key
              AND p.status IN ('PENDING', 'IN_FLIGHT', 'FAILED')
              AND p.seq < o.seq)
        ORDER BY o.seq
      )
      UPDATE candidates
      SET attempts = CASE WHEN status = 'IN_FLIGHT' AND send_started_at IS NOT NULL THEN attempts + 1 ELSE attempts END,
          status = 'IN_FLIGHT', claimed_at = :now, claimed_by = :claimedBy, send_started_at = NULL
      OUTPUT inserted.id, inserted.origin, inserted.routing_key, inserted.topic,
          inserted.message_key, inserted.payload, inserted.status, inserted.attempts,
          inserted.error, inserted.occurred_at, inserted.next_attempt_at, inserted.published_at,
          inserted.claimed_at, inserted.claimed_by, inserted.seq, inserted.send_started_at
      """;

  private static final String PURGE_PUBLISHED_SQL =
      """
      DELETE TOP (:limit) FROM outbox_message
      WHERE origin = :origin AND status = 'PUBLISHED' AND published_at < :cutoff
      """;

  /** Creates the SQL Server dialect. */
  public SqlServerOutboxDialect() {
    // stateless
  }

  @Override
  public String name() {
    return "sqlserver";
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
