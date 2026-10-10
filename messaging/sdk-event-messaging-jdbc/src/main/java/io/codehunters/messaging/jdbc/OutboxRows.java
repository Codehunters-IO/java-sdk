package io.codehunters.messaging.jdbc;

import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.OutboxStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;

/**
 * Maps {@code outbox_message} rows. The columns are {@code TIMESTAMP} without time zone holding UTC
 * wall-clock time, so instants are bound and read as UTC {@link LocalDateTime}s.
 */
final class OutboxRows {

  static final RowMapper<OutboxMessage<String>> MAPPER = OutboxRows::map;

  private OutboxRows() {}

  static LocalDateTime utc(Instant instant) {
    return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private static OutboxMessage<String> map(ResultSet rs, int rowNum) throws SQLException {
    return OutboxMessage.<String>builder()
        .id(rs.getObject("id", UUID.class))
        .origin(rs.getString("origin"))
        .routingKey(rs.getString("routing_key"))
        .topic(rs.getString("topic"))
        .key(rs.getString("message_key"))
        .message(rs.getString("payload"))
        .seq(seq(rs))
        .traceparent(rs.getString("traceparent"))
        .status(OutboxStatus.valueOf(rs.getString("status")))
        .attempts(rs.getInt("attempts"))
        .error(rs.getString("error"))
        .claimedBy(rs.getString("claimed_by"))
        .occurredAt(instant(rs, "occurred_at"))
        .nextAttemptAt(instant(rs, "next_attempt_at"))
        .publishedAt(instant(rs, "published_at"))
        .claimedAt(instant(rs, "claimed_at"))
        .sendStartedAt(instant(rs, "send_started_at"))
        .build();
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    LocalDateTime value = rs.getObject(column, LocalDateTime.class);
    return value == null ? null : value.toInstant(ZoneOffset.UTC);
  }

  private static Long seq(ResultSet rs) throws SQLException {
    long seq = rs.getLong("seq");
    return rs.wasNull() ? null : seq;
  }
}
