package io.codehunters.messaging.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.OutboxBacklog;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/** The backlog counts the origin's PENDING and IN_FLIGHT rows and dates the oldest of them. */
class OutboxBacklogIT {

  private static final String ORIGIN = "sentinel-ms-test";
  private static final String OTHER_ORIGIN = "other-ms";
  private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
  private static final Instant OLDEST = NOW.minus(Duration.ofMinutes(30));

  private JdbcClient jdbcClient;
  private OutboxJdbcAdapter adapter;

  @BeforeEach
  void setUp() {
    DataSource dataSource = PostgresTestSupport.dataSource();
    PostgresTestSupport.truncate();
    jdbcClient = JdbcClient.create(dataSource);
    var settings = new OutboxAdapterSettings(ORIGIN, Clock.fixed(NOW, ZoneOffset.UTC), () -> {});
    adapter = new OutboxJdbcAdapter(dataSource, JsonMapper.builder().build(), settings);
  }

  @Test
  void should_report_empty_backlog_when_nothing_waits() {
    insert(ORIGIN, "PUBLISHED", OLDEST);
    insert(ORIGIN, "FAILED", OLDEST);

    assertThat(adapter.backlog()).contains(new OutboxBacklog(0, Optional.empty()));
  }

  @Test
  void should_count_pending_and_in_flight_rows_of_origin_and_date_oldest() {
    insert(OTHER_ORIGIN, "PENDING", OLDEST.minus(Duration.ofDays(1)));
    insert(ORIGIN, "PUBLISHED", OLDEST.minus(Duration.ofHours(1)));
    insert(ORIGIN, "IN_FLIGHT", OLDEST);
    insert(ORIGIN, "PENDING", NOW.minus(Duration.ofMinutes(5)));
    insert(ORIGIN, "FAILED", NOW);
    insert(ORIGIN, "PENDING", NOW);

    assertThat(adapter.backlog()).contains(new OutboxBacklog(3, Optional.of(OLDEST)));
  }

  private void insert(String origin, String status, Instant occurredAt) {
    jdbcClient
        .sql(
            """
            INSERT INTO outbox_message (id, origin, topic, payload, status, occurred_at)
            VALUES (gen_random_uuid(), :origin, 't-main', 'p', :status, :at)
            """)
        .param("origin", origin)
        .param("status", status)
        .param("at", OutboxRows.utc(occurredAt))
        .update();
  }
}
