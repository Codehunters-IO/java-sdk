package io.codehunters.messaging.infrastructure.drivenadapters.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.domain.model.OutboxMessage;
import io.codehunters.messaging.domain.model.OutboxStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class OutboxJdbcAdapterPostgresIT {

  private static final String ORIGIN = "sentinel-ms-test";
  private static final Duration TTL = Duration.ofMinutes(5);

  private DataSource dataSource;
  private JdbcClient jdbcClient;
  private OutboxJdbcAdapter adapter;

  @BeforeEach
  void setUp() {
    dataSource = PostgresTestSupport.dataSource();
    PostgresTestSupport.truncate();
    jdbcClient = JdbcClient.create(dataSource);
    adapter = new OutboxJdbcAdapter(dataSource, JsonMapper.builder().build(), ORIGIN);
  }

  @Test
  void should_load_reference_ddl_when_container_starts() {
    var tables =
        jdbcClient
            .sql(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'"
                    + " AND table_name IN ('outbox_message', 'shedlock')")
            .query(String.class)
            .list();
    var seqIdentity =
        jdbcClient
            .sql(
                "SELECT is_identity FROM information_schema.columns"
                    + " WHERE table_name = 'outbox_message' AND column_name = 'seq'")
            .query(String.class)
            .single();

    assertThat(tables).containsExactlyInAnyOrder("outbox_message", "shedlock");
    assertThat(seqIdentity).isEqualTo("YES");
  }

  @Test
  void should_round_trip_message_when_saved_and_claimed_on_postgres() {
    adapter.save(message("k1", Map.of("amount", 10)));

    var claimed = adapter.claimPendingMessages(ORIGIN, 10, "w1", TTL);

    assertThat(claimed).hasSize(1);
    assertThat(claimed.get(0).getStatus()).isEqualTo(OutboxStatus.IN_FLIGHT);
    assertThat(claimed.get(0).getSeq()).isNotNull();
    assertThat(claimed.get(0).getMessage()).isEqualTo("{\"amount\":10}");
  }

  @Test
  void should_assign_increasing_seq_when_saved_in_order() {
    var ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    tx.executeWithoutResult(status -> ids.forEach(id -> adapter.save(withId(id))));

    var seqs = ids.stream().map(this::seqOf).toList();

    assertThat(seqs).isSorted().doesNotHaveDuplicates();
  }

  @Test
  void should_mark_published_when_updated_after_claim() {
    adapter.save(message("k1", "payload"));
    var claimed = adapter.claimPendingMessages(ORIGIN, 10, "w1", TTL).get(0);
    var publishedAt = LocalDateTime.now(ZoneOffset.UTC).withNano(0);

    claimed.setStatus(OutboxStatus.PUBLISHED);
    claimed.setPublishedAt(publishedAt);
    adapter.update(claimed);

    var row =
        jdbcClient
            .sql("SELECT status, published_at, claimed_by FROM outbox_message WHERE id = :id")
            .param("id", claimed.getId())
            .query()
            .singleRow();
    assertThat(row.get("status")).isEqualTo("PUBLISHED");
    assertThat(row.get("published_at")).isNotNull();
    assertThat(row.get("claimed_by")).isNull();
  }

  @Test
  void should_fence_out_stale_worker_when_claim_was_taken_over_after_ttl() {
    adapter.save(message("k1", "payload"));
    var staleClaim = adapter.claimPendingMessages(ORIGIN, 10, "w1", TTL).get(0);
    jdbcClient
        .sql("UPDATE outbox_message SET claimed_at = :old WHERE id = :id")
        .param("old", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10))
        .param("id", staleClaim.getId())
        .update();
    var liveClaim = adapter.claimPendingMessages(ORIGIN, 10, "w2", TTL).get(0);

    staleClaim.setStatus(OutboxStatus.PUBLISHED);
    adapter.update(staleClaim);

    var afterStale = rowOf(staleClaim.getId());
    assertThat(afterStale.get("status")).isEqualTo("IN_FLIGHT");
    assertThat(afterStale.get("claimed_by")).isEqualTo(liveClaim.getClaimedBy());

    liveClaim.setStatus(OutboxStatus.PUBLISHED);
    adapter.update(liveClaim);

    assertThat(rowOf(liveClaim.getId()).get("status")).isEqualTo("PUBLISHED");
  }

  private Map<String, Object> rowOf(UUID id) {
    return jdbcClient
        .sql("SELECT status, claimed_by FROM outbox_message WHERE id = :id")
        .param("id", id)
        .query()
        .singleRow();
  }

  private Long seqOf(UUID id) {
    return jdbcClient
        .sql("SELECT seq FROM outbox_message WHERE id = :id")
        .param("id", id)
        .query(Long.class)
        .single();
  }

  private static <T> OutboxMessage<T> message(String key, T payload) {
    return OutboxMessage.<T>builder().topic("t").key(key).message(payload).build();
  }

  private static OutboxMessage<String> withId(UUID id) {
    return OutboxMessage.<String>builder().id(id).topic("t").key("k").message("p").build();
  }

  @Test
  void should_increment_attempts_when_reclaiming_stale_row_whose_send_had_started() {
    UUID id = UUID.randomUUID();
    adapter.save(unsent(id));
    recordOneFailedAttempt(id);
    markStaleInFlight(id);
    markSendStartedByDeadInstance(id);

    var reclaimed = adapter.claimPendingMessages(ORIGIN, 10, "live", Duration.ofMinutes(5));

    assertThat(reclaimed).singleElement().extracting(OutboxMessage::getAttempts).isEqualTo(2);
    assertThat(attemptsOf(id)).isEqualTo(2);
  }

  @Test
  void should_keep_attempts_when_claiming_pending_row() {
    UUID id = UUID.randomUUID();
    adapter.save(unsent(id));
    recordOneFailedAttempt(id);

    var claimed = adapter.claimPendingMessages(ORIGIN, 10, "live", Duration.ofMinutes(5));

    assertThat(claimed).singleElement().extracting(OutboxMessage::getAttempts).isEqualTo(1);
    assertThat(attemptsOf(id)).isEqualTo(1);
  }

  private void markSendStartedByDeadInstance(UUID id) {
    jdbcClient
        .sql("UPDATE outbox_message SET send_started_at = :old WHERE id = :id")
        .param("old", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(9))
        .param("id", id)
        .update();
  }

  private void markStaleInFlight(UUID id) {
    jdbcClient
        .sql(
            "UPDATE outbox_message SET status = 'IN_FLIGHT', claimed_at = :old,"
                + " claimed_by = 'dead-instance' WHERE id = :id")
        .param("old", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10))
        .param("id", id)
        .update();
  }

  private Integer attemptsOf(UUID id) {
    return jdbcClient
        .sql("SELECT attempts FROM outbox_message WHERE id = :id")
        .param("id", id)
        .query(Integer.class)
        .single();
  }

  private void recordOneFailedAttempt(UUID id) {
    jdbcClient
        .sql("UPDATE outbox_message SET attempts = 1 WHERE id = :id")
        .param("id", id)
        .update();
  }

  private static OutboxMessage<String> unsent(UUID id) {
    return OutboxMessage.<String>builder()
        .id(id)
        .topic("t")
        .key("k")
        .message("p")
        .occurredAt(LocalDateTime.now(ZoneOffset.UTC))
        .build();
  }
}
