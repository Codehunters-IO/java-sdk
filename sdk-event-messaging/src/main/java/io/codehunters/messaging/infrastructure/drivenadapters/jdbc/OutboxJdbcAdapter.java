package io.codehunters.messaging.infrastructure.drivenadapters.jdbc;

import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.codehunters.messaging.domain.model.OutboxMessage;
import io.codehunters.messaging.domain.model.OutboxStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * JDBC-backed {@link OutboxRepositoryPort}. Atomically claims a batch of pending or stale rows by
 * marking them {@code IN_FLIGHT} with a claim marker before returning them, so concurrent dispatch
 * workers do not double-publish.
 */
@Slf4j
public class OutboxJdbcAdapter implements OutboxRepositoryPort {

  private static final String INSERT_SQL =
      """
      INSERT INTO outbox_message (id, origin, routing_key, topic, message_key, payload, status,
          attempts, error, occurred_at, next_attempt_at, published_at, claimed_at, claimed_by)
      VALUES (:id, :origin, :routingKey, :topic, :messageKey, :payload, :status, :attempts,
          :error, :occurredAt, :nextAttemptAt, :publishedAt, :claimedAt, :claimedBy)
      """;

  private static final String CLAIM_UPDATE_SQL =
      """
      UPDATE outbox_message
      SET status = 'IN_FLIGHT', claimed_at = :claimedAt, claimed_by = :claimedBy
      WHERE id IN (
          SELECT id FROM outbox_message
          WHERE origin = :origin
            AND (
              (status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= :nowTs))
              OR (status = 'IN_FLIGHT' AND claimed_at IS NOT NULL AND claimed_at <= :ttlBoundary)
            )
          ORDER BY occurred_at ASC
          OFFSET 0 ROWS FETCH NEXT :limit ROWS ONLY
      )
      """;

  private static final String SELECT_CLAIMED_SQL =
      """
      SELECT id, origin, routing_key, topic, message_key, payload, status, attempts,
          error, occurred_at, next_attempt_at, published_at, claimed_at, claimed_by, seq
      FROM outbox_message
      WHERE origin = :origin AND claimed_by = :claimedBy AND status = 'IN_FLIGHT'
      ORDER BY occurred_at ASC
      """;

  private static final String UPDATE_SQL =
      """
      UPDATE outbox_message
      SET status = :status, attempts = :attempts, error = :error,
          published_at = :publishedAt, next_attempt_at = :nextAttemptAt,
          claimed_at = :claimedAt, claimed_by = :claimedBy
      WHERE id = :id
      """;

  private static final RowMapper<OutboxMessage<String>> ROW_MAPPER =
      (rs, rowNum) ->
          OutboxMessage.<String>builder()
              .id(rs.getObject("id", UUID.class))
              .origin(rs.getString("origin"))
              .routingKey(rs.getString("routing_key"))
              .topic(rs.getString("topic"))
              .key(rs.getString("message_key"))
              .message(rs.getString("payload"))
              .status(OutboxStatus.valueOf(rs.getString("status")))
              .attempts(rs.getInt("attempts"))
              .error(rs.getString("error"))
              .occurredAt(rs.getObject("occurred_at", LocalDateTime.class))
              .nextAttemptAt(rs.getObject("next_attempt_at", LocalDateTime.class))
              .publishedAt(rs.getObject("published_at", LocalDateTime.class))
              .claimedAt(rs.getObject("claimed_at", LocalDateTime.class))
              .claimedBy(rs.getString("claimed_by"))
              .seq(readSeq(rs))
              .build();

  private final JdbcClient jdbcClient;
  private final JsonMapper outboxJsonMapper;
  private final String origin;

  /**
   * Creates the adapter.
   *
   * @param dataSource the target data source
   * @param outboxJsonMapper the JSON mapper for outbox payloads
   * @param origin the default origin stamped on messages that do not carry one
   */
  public OutboxJdbcAdapter(
      DataSource dataSource,
      @Qualifier("outboxJsonMapper") JsonMapper outboxJsonMapper,
      String origin) {
    this.jdbcClient = JdbcClient.create(dataSource);
    this.outboxJsonMapper = outboxJsonMapper;
    this.origin = origin;
  }

  @Override
  public <T> void save(OutboxMessage<T> outboxMessage) {
    log.debug("[START] - save outbox message for topic: {}", outboxMessage.getTopic());
    var id = outboxMessage.getId() != null ? outboxMessage.getId() : UUID.randomUUID();
    String payload = serializePayload(outboxMessage.getMessage());
    String effectiveOrigin = resolveOrigin(outboxMessage.getOrigin());

    jdbcClient
        .sql(INSERT_SQL)
        .param("id", id)
        .param("origin", effectiveOrigin)
        .param("routingKey", outboxMessage.getRoutingKey())
        .param("topic", outboxMessage.getTopic())
        .param("messageKey", outboxMessage.getKey())
        .param("payload", payload)
        .param("status", OutboxStatus.PENDING.name())
        .param("attempts", 0)
        .param("error", null)
        .param("occurredAt", outboxMessage.getOccurredAt())
        .param("nextAttemptAt", outboxMessage.getNextAttemptAt())
        .param("publishedAt", null)
        .param("claimedAt", null)
        .param("claimedBy", null)
        .update();

    log.debug("[END] - saved outbox message with id: {}", id);
  }

  @Override
  public List<OutboxMessage<String>> claimPendingMessages(
      String origin, int batchSize, String claimedBy, Duration claimTtl) {
    log.debug("[START] - claim pending outbox messages for origin={}", origin);
    LocalDateTime now = LocalDateTime.now();
    String claimMarker = claimedBy + ":" + UUID.randomUUID();
    LocalDateTime ttlBoundary = now.minus(claimTtl);

    int updated =
        jdbcClient
            .sql(CLAIM_UPDATE_SQL)
            .param("claimedAt", now)
            .param("claimedBy", claimMarker)
            .param("origin", origin)
            .param("nowTs", now)
            .param("ttlBoundary", ttlBoundary)
            .param("limit", batchSize)
            .update();

    if (updated == 0) {
      log.debug("[END] - claimed 0 outbox messages");
      return List.of();
    }

    var result =
        jdbcClient
            .sql(SELECT_CLAIMED_SQL)
            .param("origin", origin)
            .param("claimedBy", claimMarker)
            .query(ROW_MAPPER)
            .list();

    log.debug("[END] - claimed {} outbox messages (updated={})", result.size(), updated);
    return result;
  }

  @Override
  public void update(OutboxMessage<String> outboxMessage) {
    log.debug("[START] - update outbox message {}", outboxMessage.getId());
    jdbcClient
        .sql(UPDATE_SQL)
        .param("id", outboxMessage.getId())
        .param("status", outboxMessage.getStatus().name())
        .param("attempts", outboxMessage.getAttempts())
        .param("error", outboxMessage.getError())
        .param("publishedAt", outboxMessage.getPublishedAt())
        .param("nextAttemptAt", outboxMessage.getNextAttemptAt())
        .param("claimedAt", keepsClaim(outboxMessage) ? outboxMessage.getClaimedAt() : null)
        .param("claimedBy", keepsClaim(outboxMessage) ? outboxMessage.getClaimedBy() : null)
        .update();

    log.debug("[END] - updated outbox message {}", outboxMessage.getId());
  }

  private static boolean keepsClaim(OutboxMessage<String> outboxMessage) {
    return outboxMessage.getStatus() == OutboxStatus.IN_FLIGHT;
  }

  private static Long readSeq(ResultSet rs) throws SQLException {
    long seq = rs.getLong("seq");
    return rs.wasNull() ? null : seq;
  }

  private String resolveOrigin(String messageOrigin) {
    if (messageOrigin != null && !messageOrigin.isBlank()) {
      return messageOrigin;
    }
    return origin;
  }

  private <T> String serializePayload(T message) {
    if (message instanceof String s) {
      return s;
    }
    try {
      return outboxJsonMapper.writeValueAsString(message);
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialize outbox message payload", e);
    }
  }
}
