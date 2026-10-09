package io.codehunters.messaging.jdbc;

import io.codehunters.messaging.ClaimRequest;
import io.codehunters.messaging.OutboxDispatchStore;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.OutboxStatus;
import io.codehunters.messaging.OutboxWriter;
import io.codehunters.messaging.W3cTraceParent;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * PostgreSQL-backed {@link OutboxWriter} and {@link OutboxDispatchStore}. Claims a batch of
 * claimable rows with {@code FOR UPDATE SKIP LOCKED}, so concurrent dispatch workers receive
 * disjoint rows and a row is held back while an earlier row with the same {@code (origin, topic,
 * message_key)} is unresolved. Updates of a claimed row, including the send mark written before
 * each publish, are fenced by its claim marker, so a worker whose claim was taken over after the
 * TTL cannot overwrite the new owner's state. The {@code traceparent} captured with each message is
 * stored and returned by the claim, so the publisher can continue the trace that created the row. A
 * value that is not a valid {@link W3cTraceParent} is stored as null instead of failing the
 * business transaction.
 */
public class OutboxJdbcAdapter implements OutboxWriter, OutboxDispatchStore {

  private static final Logger log = LoggerFactory.getLogger(OutboxJdbcAdapter.class);

  private static final String INSERT_SQL =
      """
      INSERT INTO outbox_message (id, origin, routing_key, topic, message_key, payload, status,
          attempts, occurred_at, next_attempt_at, traceparent)
      VALUES (:id, :origin, :routingKey, :topic, :messageKey, :payload, 'PENDING', 0,
          :occurredAt, :nextAttemptAt, :traceparent)
      """;

  // Locks the candidate rows, skips rows locked by a concurrent claimer, marks them IN_FLIGHT and
  // returns them in one statement. Only the candidate scan is locked; the per-key subquery is a
  // plain snapshot read, so an earlier same-key row being claimed by another dispatcher still
  // blocks later rows. A stale IN_FLIGHT row is charged an attempt only when send_started_at is
  // set: only then may the previous dispatcher have published it before dying.
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
      SET attempts = CASE WHEN status = 'IN_FLIGHT' AND send_started_at IS NOT NULL THEN attempts + 1 ELSE attempts END,
          status = 'IN_FLIGHT', claimed_at = :now, claimed_by = :claimedBy, send_started_at = NULL
      FROM candidates c
      WHERE m.id = c.id
      RETURNING m.id, m.origin, m.routing_key, m.topic, m.message_key, m.payload, m.status,
          m.attempts, m.error, m.occurred_at, m.next_attempt_at, m.published_at, m.claimed_at,
          m.claimed_by, m.seq, m.send_started_at, m.traceparent
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

  private static final String UPDATE_SQL =
      """
      UPDATE outbox_message
      SET status = :status, attempts = :attempts, error = :error,
          published_at = :publishedAt, next_attempt_at = :nextAttemptAt,
          claimed_at = :claimedAt, claimed_by = :claimedBy, send_started_at = :sendStartedAt
      WHERE id = :id
      """;

  private static final String UPDATE_FENCED_SQL =
      """
      UPDATE outbox_message
      SET status = :status, attempts = :attempts, error = :error,
          published_at = :publishedAt, next_attempt_at = :nextAttemptAt,
          claimed_at = :claimedAt, claimed_by = :claimedBy, send_started_at = :sendStartedAt
      WHERE id = :id AND claimed_by = :expectedClaimedBy
      """;

  private static final String MARK_SEND_STARTED_SQL =
      """
      UPDATE outbox_message
      SET send_started_at = :now
      WHERE id = :id AND claimed_by = :claimedBy AND status = 'IN_FLIGHT'
      """;

  private static final String COUNT_FAILED_SQL =
      "SELECT COUNT(*) FROM outbox_message WHERE origin = :origin AND status = 'FAILED'";

  private static final Comparator<OutboxMessage<String>> BY_SEQ =
      Comparator.comparing(OutboxMessage::seq, Comparator.nullsLast(Comparator.naturalOrder()));

  private final JdbcClient jdbcClient;
  private final JsonMapper outboxJsonMapper;
  private final OutboxAdapterSettings settings;
  private final AtomicBoolean transactionalMarkWarned = new AtomicBoolean();

  /**
   * Creates the adapter with a UTC system clock.
   *
   * @param dataSource the target data source
   * @param outboxJsonMapper the JSON mapper for outbox payloads
   * @param origin the default origin stamped on messages that do not carry one
   */
  public OutboxJdbcAdapter(
      DataSource dataSource,
      @Qualifier("outboxJsonMapper") JsonMapper outboxJsonMapper,
      String origin) {
    this(dataSource, outboxJsonMapper, OutboxAdapterSettings.defaults(origin));
  }

  /**
   * Creates the adapter. The JDBC client joins the caller's transaction through {@code
   * DataSourceUtils}, so {@link #save} commits atomically with the caller's own writes.
   *
   * @param dataSource the target data source
   * @param outboxJsonMapper the JSON mapper for outbox payloads
   * @param settings the origin and clock
   */
  public OutboxJdbcAdapter(
      DataSource dataSource,
      @Qualifier("outboxJsonMapper") JsonMapper outboxJsonMapper,
      OutboxAdapterSettings settings) {
    this.jdbcClient = JdbcClient.create(dataSource);
    this.outboxJsonMapper = outboxJsonMapper;
    this.settings = settings;
  }

  @Override
  public <T> void save(OutboxMessage<T> outboxMessage) {
    UUID id = outboxMessage.id() != null ? outboxMessage.id() : UUID.randomUUID();
    jdbcClient
        .sql(INSERT_SQL)
        .param("id", id)
        .param("origin", resolveOrigin(outboxMessage.origin()))
        .param("routingKey", outboxMessage.routingKey())
        .param("topic", outboxMessage.topic())
        .param("messageKey", outboxMessage.key())
        .param("payload", serializePayload(outboxMessage.message()))
        .param("occurredAt", OutboxRows.utc(occurredAt(outboxMessage)))
        .param("nextAttemptAt", OutboxRows.utc(outboxMessage.nextAttemptAt()))
        .param("traceparent", W3cTraceParent.validOrNull(outboxMessage.traceparent()))
        .update();
    log.debug("Saved outbox message id={} for topic {}", id, outboxMessage.topic());
  }

  @Override
  public List<OutboxMessage<String>> claim(ClaimRequest request) {
    Instant now = settings.clock().instant();
    String claimMarker = request.claimedBy() + ":" + UUID.randomUUID();
    List<OutboxMessage<String>> claimed =
        jdbcClient
            .sql(CLAIM_SQL)
            .param("origin", request.origin())
            .param("now", OutboxRows.utc(now))
            .param("ttlBoundary", OutboxRows.utc(now.minus(request.claimTtl())))
            .param("limit", request.batchSize())
            .param("claimedBy", claimMarker)
            .query(OutboxRows.MAPPER)
            .list();
    log.debug("Claimed {} outbox messages for origin={}", claimed.size(), request.origin());
    return claimed.stream().sorted(BY_SEQ).toList();
  }

  @Override
  public Optional<OutboxMessage<String>> markSendStarted(OutboxMessage<String> outboxMessage) {
    warnOnceIfInCallerTransaction();
    Instant now = settings.clock().instant();
    int marked =
        jdbcClient
            .sql(MARK_SEND_STARTED_SQL)
            .param("now", OutboxRows.utc(now))
            .param("id", outboxMessage.id())
            .param("claimedBy", outboxMessage.claimedBy())
            .update();
    return marked == 0 ? Optional.empty() : Optional.of(outboxMessage.sendStarted(now));
  }

  // The mark must commit before the publish: inside a caller transaction it commits only after the
  // whole dispatch, so a crash loses it and the row is never charged.
  private void warnOnceIfInCallerTransaction() {
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && transactionalMarkWarned.compareAndSet(false, true)) {
      log.warn(
          "Outbox send marks are running inside a caller transaction; dispatch must run outside"
              + " one, or a crash after publishing leaves the row uncharged");
    }
  }

  @Override
  public void update(OutboxMessage<String> outboxMessage) {
    String expectedClaimedBy = outboxMessage.claimedBy();
    var statement =
        expectedClaimedBy == null
            ? jdbcClient.sql(UPDATE_SQL)
            : jdbcClient.sql(UPDATE_FENCED_SQL).param("expectedClaimedBy", expectedClaimedBy);
    if (bindUpdate(statement, outboxMessage).update() == 0) {
      log.warn(
          "Outbox update fenced out id={} status={}", outboxMessage.id(), outboxMessage.status());
    }
  }

  @Override
  public int purgePublished(Instant cutoff, int batchSize) {
    int deleted =
        jdbcClient
            .sql(PURGE_PUBLISHED_SQL)
            .param("origin", settings.origin())
            .param("cutoff", OutboxRows.utc(cutoff))
            .param("limit", batchSize)
            .update();
    log.debug("Purged {} published outbox messages for origin={}", deleted, settings.origin());
    return deleted;
  }

  @Override
  public long countFailed() {
    return jdbcClient
        .sql(COUNT_FAILED_SQL)
        .param("origin", settings.origin())
        .query(Long.class)
        .single();
  }

  private static JdbcClient.StatementSpec bindUpdate(
      JdbcClient.StatementSpec statement, OutboxMessage<String> outboxMessage) {
    boolean keepsClaim = outboxMessage.status() == OutboxStatus.IN_FLIGHT;
    return statement
        .param("id", outboxMessage.id())
        .param("status", outboxMessage.status().name())
        .param("attempts", outboxMessage.attempts())
        .param("error", outboxMessage.error())
        .param("publishedAt", OutboxRows.utc(outboxMessage.publishedAt()))
        .param("nextAttemptAt", OutboxRows.utc(outboxMessage.nextAttemptAt()))
        .param("claimedAt", keepsClaim ? OutboxRows.utc(outboxMessage.claimedAt()) : null)
        .param("claimedBy", keepsClaim ? outboxMessage.claimedBy() : null)
        .param("sendStartedAt", keepsClaim ? OutboxRows.utc(outboxMessage.sendStartedAt()) : null);
  }

  private Instant occurredAt(OutboxMessage<?> outboxMessage) {
    Instant occurredAt = outboxMessage.occurredAt();
    return occurredAt != null ? occurredAt : settings.clock().instant();
  }

  private String resolveOrigin(String messageOrigin) {
    if (messageOrigin != null && !messageOrigin.isBlank()) {
      return messageOrigin;
    }
    return settings.origin();
  }

  private <T> String serializePayload(T message) {
    if (message instanceof String s) {
      return s;
    }
    try {
      return outboxJsonMapper.writeValueAsString(message);
    } catch (RuntimeException e) {
      throw new IllegalStateException("Failed to serialize outbox message payload", e);
    }
  }
}
