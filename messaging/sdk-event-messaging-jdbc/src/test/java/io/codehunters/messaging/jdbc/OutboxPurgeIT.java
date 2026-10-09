package io.codehunters.messaging.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

class OutboxPurgeIT {

  private static final String ORIGIN = "o1";
  private static final String OTHER_ORIGIN = "o2";
  private static final int DEFAULT_BATCH = 500;

  private final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
  private JdbcClient jdbcClient;
  private OutboxJdbcAdapter adapter;

  @BeforeEach
  void setUp() {
    DataSource dataSource = PostgresTestSupport.dataSource();
    PostgresTestSupport.truncate();
    jdbcClient = JdbcClient.create(dataSource);
    adapter = new OutboxJdbcAdapter(dataSource, JsonMapper.builder().build(), ORIGIN);
  }

  @Test
  void should_delete_only_old_published_rows_of_origin_when_purging() {
    insert(ORIGIN, "PUBLISHED", now.minusDays(8));
    insert(ORIGIN, "PUBLISHED", now.minusDays(8));
    UUID recent = insert(ORIGIN, "PUBLISHED", now.minusDays(1));
    UUID failed = insert(ORIGIN, "FAILED", now.minusDays(30));
    UUID pending = insert(ORIGIN, "PENDING", null);
    UUID otherOrigin = insert(OTHER_ORIGIN, "PUBLISHED", now.minusDays(8));

    int deleted = adapter.purgePublished(now.minusDays(7), DEFAULT_BATCH);

    assertThat(deleted).isEqualTo(2);
    assertThat(remainingIds()).containsExactlyInAnyOrder(recent, failed, pending, otherOrigin);
  }

  @Test
  void should_bound_each_purge_by_batch_size_when_many_rows_are_old() {
    IntStream.range(0, 5).forEach(i -> insert(ORIGIN, "PUBLISHED", now.minusDays(8)));

    int first = adapter.purgePublished(now.minusDays(7), 2);
    int second = adapter.purgePublished(now.minusDays(7), 2);
    int third = adapter.purgePublished(now.minusDays(7), 2);

    assertThat(new int[] {first, second, third}).containsExactly(2, 2, 1);
  }

  @Test
  void should_count_failed_rows_of_origin_only_when_counting_on_postgres() {
    insert(ORIGIN, "FAILED", now.minusDays(1));
    insert(ORIGIN, "FAILED", now.minusDays(2));
    insert(ORIGIN, "PUBLISHED", now.minusDays(1));
    insert(OTHER_ORIGIN, "FAILED", now.minusDays(1));

    assertThat(adapter.countFailed()).isEqualTo(2L);
  }

  private UUID insert(String origin, String status, LocalDateTime publishedAt) {
    UUID id = UUID.randomUUID();
    jdbcClient
        .sql(
            "INSERT INTO outbox_message (id, origin, topic, payload, status, occurred_at,"
                + " published_at) VALUES (:id, :origin, 't', 'p', :status, :occurredAt,"
                + " :publishedAt)")
        .param("id", id)
        .param("origin", origin)
        .param("status", status)
        .param("occurredAt", now.minusDays(40))
        .param("publishedAt", publishedAt)
        .update();
    return id;
  }

  private List<UUID> remainingIds() {
    return jdbcClient.sql("SELECT id FROM outbox_message").query(UUID.class).list();
  }
}
