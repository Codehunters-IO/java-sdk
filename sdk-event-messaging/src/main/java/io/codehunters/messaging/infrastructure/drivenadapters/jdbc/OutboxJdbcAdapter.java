package io.codehunters.messaging.infrastructure.drivenadapters.jdbc;

import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.codehunters.messaging.domain.model.OutboxMessage;
import io.codehunters.messaging.domain.model.OutboxStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * JDBC-backed {@link OutboxRepositoryPort}. Claims a batch of claimable rows through the
 * database-specific {@link OutboxDialect}, so concurrent dispatch workers receive disjoint rows and
 * a row is held back while an earlier row with the same {@code (origin, topic, message_key)} is
 * unresolved. Updates of a claimed row are fenced by its claim marker, so a worker whose claim was
 * taken over after the TTL cannot overwrite the new owner's state.
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

  private static final String SELECT_CLAIMED_SQL =
      """
      SELECT id, origin, routing_key, topic, message_key, payload, status, attempts,
          error, occurred_at, next_attempt_at, published_at, claimed_at, claimed_by, seq
      FROM outbox_message
      WHERE origin = :origin AND claimed_by = :claimedBy AND status = 'IN_FLIGHT'
      """;

  private static final String UPDATE_SQL =
      """
      UPDATE outbox_message
      SET status = :status, attempts = :attempts, error = :error,
          published_at = :publishedAt, next_attempt_at = :nextAttemptAt,
          claimed_at = :claimedAt, claimed_by = :claimedBy
      WHERE id = :id
      """;

  private static final String UPDATE_FENCED_SQL =
      """
      UPDATE outbox_message
      SET status = :status, attempts = :attempts, error = :error,
          published_at = :publishedAt, next_attempt_at = :nextAttemptAt,
          claimed_at = :claimedAt, claimed_by = :claimedBy
      WHERE id = :id AND claimed_by = :expectedClaimedBy
      """;

  private static final String COUNT_FAILED_SQL =
      "SELECT COUNT(*) FROM outbox_message WHERE origin = :origin AND status = 'FAILED'";

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

  private static final Comparator<OutboxMessage<String>> BY_SEQ =
      Comparator.comparing(OutboxMessage::getSeq, Comparator.nullsLast(Comparator.naturalOrder()));

  private final JdbcClient jdbcClient;
  private final JsonMapper outboxJsonMapper;
  private final OutboxAdapterSettings settings;

  /**
   * Creates the adapter with the dialect auto-detected from {@code dataSource} and a UTC clock.
   *
   * @param dataSource the target data source
   * @param outboxJsonMapper the JSON mapper for outbox payloads
   * @param origin the default origin stamped on messages that do not carry one
   */
  public OutboxJdbcAdapter(
      DataSource dataSource,
      @Qualifier("outboxJsonMapper") JsonMapper outboxJsonMapper,
      String origin) {
    this(dataSource, outboxJsonMapper, OutboxAdapterSettings.defaults(dataSource, origin));
  }

  /**
   * Creates the adapter. The JDBC client joins the caller's transaction through {@code
   * DataSourceUtils}, so {@link #save} commits atomically with the caller's own writes.
   *
   * @param dataSource the target data source
   * @param outboxJsonMapper the JSON mapper for outbox payloads
   * @param settings the origin, claim dialect and clock
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
    log.debug("[START] - save outbox message for topic: {}", outboxMessage.getTopic());
    var id = outboxMessage.getId() != null ? outboxMessage.getId() : UUID.randomUUID();
    jdbcClient
        .sql(INSERT_SQL)
        .param("id", id)
        .param("origin", resolveOrigin(outboxMessage.getOrigin()))
        .param("routingKey", outboxMessage.getRoutingKey())
        .param("topic", outboxMessage.getTopic())
        .param("messageKey", outboxMessage.getKey())
        .param("payload", serializePayload(outboxMessage.getMessage()))
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
    LocalDateTime now = LocalDateTime.now(settings.clock());
    String claimMarker = claimedBy + ":" + UUID.randomUUID();
    var claim =
        jdbcClient
            .sql(settings.dialect().claimSql())
            .param("origin", origin)
            .param("now", now)
            .param("ttlBoundary", now.minus(claimTtl))
            .param("limit", batchSize)
            .param("claimedBy", claimMarker);
    var claimed =
        settings.dialect().claimReturnsRows()
            ? claim.query(ROW_MAPPER).list()
            : selectClaimed(claim.update(), origin, claimMarker);
    log.debug("[END] - claimed {} outbox messages", claimed.size());
    return claimed.stream().sorted(BY_SEQ).toList();
  }

  @Override
  public void update(OutboxMessage<String> outboxMessage) {
    log.debug("[START] - update outbox message {}", outboxMessage.getId());
    String expectedClaimedBy = outboxMessage.getClaimedBy();
    var statement =
        expectedClaimedBy == null
            ? jdbcClient.sql(UPDATE_SQL)
            : jdbcClient.sql(UPDATE_FENCED_SQL).param("expectedClaimedBy", expectedClaimedBy);
    int updated = bindUpdate(statement, outboxMessage).update();
    if (updated == 0) {
      log.warn(
          "Outbox update fenced out id={} status={}",
          outboxMessage.getId(),
          outboxMessage.getStatus());
      return;
    }
    log.debug("[END] - updated outbox message {}", outboxMessage.getId());
  }

  @Override
  public int purgePublished(LocalDateTime cutoff, int batchSize) {
    int deleted =
        jdbcClient
            .sql(settings.dialect().purgePublishedSql())
            .param("origin", settings.origin())
            .param("cutoff", cutoff)
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

  private List<OutboxMessage<String>> selectClaimed(int updated, String origin, String marker) {
    if (updated == 0) {
      return List.of();
    }
    return jdbcClient
        .sql(SELECT_CLAIMED_SQL)
        .param("origin", origin)
        .param("claimedBy", marker)
        .query(ROW_MAPPER)
        .list();
  }

  private static JdbcClient.StatementSpec bindUpdate(
      JdbcClient.StatementSpec statement, OutboxMessage<String> outboxMessage) {
    boolean keepsClaim = outboxMessage.getStatus() == OutboxStatus.IN_FLIGHT;
    return statement
        .param("id", outboxMessage.getId())
        .param("status", outboxMessage.getStatus().name())
        .param("attempts", outboxMessage.getAttempts())
        .param("error", outboxMessage.getError())
        .param("publishedAt", outboxMessage.getPublishedAt())
        .param("nextAttemptAt", outboxMessage.getNextAttemptAt())
        .param("claimedAt", keepsClaim ? outboxMessage.getClaimedAt() : null)
        .param("claimedBy", keepsClaim ? outboxMessage.getClaimedBy() : null);
  }

  private static Long readSeq(ResultSet rs) throws SQLException {
    long seq = rs.getLong("seq");
    return rs.wasNull() ? null : seq;
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
    } catch (Exception e) {
      throw new RuntimeException("Failed to serialize outbox message payload", e);
    }
  }
}
