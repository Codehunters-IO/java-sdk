package io.codehunters.messaging.infrastructure.drivenadapters.jdbc;

/**
 * Portable claim for databases without row-skipping locks (H2 in tests). The update marks the
 * candidates with the claim marker and returns no rows; the caller selects the claimed rows by
 * {@code claimed_by} afterwards. Without row locking two concurrent dispatchers can race on the
 * same candidates, so this dialect is only chosen for H2 or by explicit configuration.
 */
public final class GenericOutboxDialect implements OutboxDialect {

  private static final String CLAIM_SQL =
      """
      UPDATE outbox_message
      SET attempts = CASE WHEN status = 'IN_FLIGHT' THEN attempts + 1 ELSE attempts END,
          status = 'IN_FLIGHT', claimed_at = :now, claimed_by = :claimedBy
      WHERE id IN (
          SELECT o.id FROM outbox_message o
          WHERE o.origin = :origin
            AND ((o.status = 'PENDING' AND (o.next_attempt_at IS NULL OR o.next_attempt_at <= :now))
              OR (o.status = 'IN_FLIGHT' AND o.claimed_at <= :ttlBoundary))
            AND NOT EXISTS (
              SELECT 1 FROM outbox_message p
              WHERE p.origin = o.origin AND p.topic = o.topic AND p.message_key = o.message_key
                AND p.status IN ('PENDING', 'IN_FLIGHT', 'FAILED')
                AND p.seq < o.seq)
          ORDER BY o.seq
          OFFSET 0 ROWS FETCH NEXT :limit ROWS ONLY
      )
      """;

  private static final String PURGE_PUBLISHED_SQL =
      """
      DELETE FROM outbox_message
      WHERE id IN (
          SELECT id FROM outbox_message
          WHERE origin = :origin AND status = 'PUBLISHED' AND published_at < :cutoff
          ORDER BY published_at
          OFFSET 0 ROWS FETCH NEXT :limit ROWS ONLY)
      """;

  /** Creates the generic dialect. */
  public GenericOutboxDialect() {
    // stateless
  }

  @Override
  public String name() {
    return "generic";
  }

  @Override
  public String claimSql() {
    return CLAIM_SQL;
  }

  @Override
  public boolean claimReturnsRows() {
    return false;
  }

  @Override
  public String purgePublishedSql() {
    return PURGE_PUBLISHED_SQL;
  }
}
