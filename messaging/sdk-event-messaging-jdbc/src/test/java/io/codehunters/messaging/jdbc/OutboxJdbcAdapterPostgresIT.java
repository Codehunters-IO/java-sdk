package io.codehunters.messaging.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.ClaimRequest;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.OutboxStatus;
import java.time.Duration;
import java.time.Instant;
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
  private static final String TRACEPARENT =
      "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

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
                "SELECT is_identity FROM information_schema.columns WHERE table_schema = 'public'"
                    + " AND table_name = 'outbox_message' AND column_name = 'seq'")
            .query(String.class)
            .single();

    assertThat(tables).containsExactlyInAnyOrder("outbox_message", "shedlock");
    assertThat(seqIdentity).isEqualTo("YES");
  }

  @Test
  void should_round_trip_message_when_saved_and_claimed_on_postgres() {
    adapter.save(message("k1", Map.of("amount", 10)));

    var claimed = adapter.claim(new ClaimRequest(ORIGIN, "w1", 10, TTL));

    assertThat(claimed).hasSize(1);
    assertThat(claimed.get(0).status()).isEqualTo(OutboxStatus.IN_FLIGHT);
    assertThat(claimed.get(0).seq()).isNotNull();
    assertThat(claimed.get(0).message()).isEqualTo("{\"amount\":10}");
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
    var claimed = adapter.claim(new ClaimRequest(ORIGIN, "w1", 10, TTL)).get(0);

    adapter.update(claimed.published(Instant.now()));

    var row =
        jdbcClient
            .sql("SELECT status, published_at, claimed_by FROM outbox_message WHERE id = :id")
            .param("id", claimed.id())
            .query()
            .singleRow();
    assertThat(row.get("status")).isEqualTo("PUBLISHED");
    assertThat(row.get("published_at")).isNotNull();
    assertThat(row.get("claimed_by")).isNull();
  }

  @Test
  void should_fence_out_stale_worker_when_claim_was_taken_over_after_ttl() {
    adapter.save(message("k1", "payload"));
    var staleClaim = adapter.claim(new ClaimRequest(ORIGIN, "w1", 10, TTL)).get(0);
    jdbcClient
        .sql("UPDATE outbox_message SET claimed_at = :old WHERE id = :id")
        .param("old", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10))
        .param("id", staleClaim.id())
        .update();
    var liveClaim = adapter.claim(new ClaimRequest(ORIGIN, "w2", 10, TTL)).get(0);

    adapter.update(staleClaim.published(Instant.now()));

    var afterStale = rowOf(staleClaim.id());
    assertThat(afterStale.get("status")).isEqualTo("IN_FLIGHT");
    assertThat(afterStale.get("claimed_by")).isEqualTo(liveClaim.claimedBy());

    adapter.update(liveClaim.published(Instant.now()));

    assertThat(rowOf(liveClaim.id()).get("status")).isEqualTo("PUBLISHED");
  }

  private Map<String, Object> rowOf(UUID id) {
    return jdbcClient
        .sql("SELECT status, claimed_by, traceparent FROM outbox_message WHERE id = :id")
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

    var reclaimed = adapter.claim(new ClaimRequest(ORIGIN, "live", 10, Duration.ofMinutes(5)));

    assertThat(reclaimed).singleElement().extracting(OutboxMessage::attempts).isEqualTo(2);
    assertThat(attemptsOf(id)).isEqualTo(2);
  }

  @Test
  void should_keep_attempts_when_claiming_pending_row() {
    UUID id = UUID.randomUUID();
    adapter.save(unsent(id));
    recordOneFailedAttempt(id);

    var claimed = adapter.claim(new ClaimRequest(ORIGIN, "live", 10, Duration.ofMinutes(5)));

    assertThat(claimed).singleElement().extracting(OutboxMessage::attempts).isEqualTo(1);
    assertThat(attemptsOf(id)).isEqualTo(1);
  }

  @Test
  void should_return_stored_traceparent_when_message_is_claimed() {
    adapter.save(unsent(UUID.randomUUID()).toBuilder().traceparent(TRACEPARENT).build());

    var claimed = adapter.claim(new ClaimRequest(ORIGIN, "w1", 10, TTL));

    assertThat(claimed)
        .singleElement()
        .extracting(OutboxMessage::traceparent)
        .isEqualTo(TRACEPARENT);
  }

  @Test
  void should_persist_traceparent_column_when_message_is_saved() {
    UUID id = UUID.randomUUID();
    adapter.save(unsent(id).toBuilder().traceparent(TRACEPARENT).build());

    assertThat(rowOf(id).get("traceparent")).isEqualTo(TRACEPARENT);
  }

  @Test
  void should_store_null_traceparent_when_saved_value_is_longer_than_55_characters() {
    UUID id = UUID.randomUUID();
    adapter.save(unsent(id).toBuilder().traceparent(TRACEPARENT + "-extra").build());

    assertThat(rowOf(id).get("traceparent")).isNull();
  }

  @Test
  void should_store_null_traceparent_when_saved_value_is_malformed() {
    UUID id = UUID.randomUUID();
    adapter.save(unsent(id).toBuilder().traceparent("00-not-a-trace-01").build());

    assertThat(rowOf(id).get("traceparent")).isNull();
  }

  @Test
  void should_claim_null_traceparent_when_message_was_saved_without_one() {
    adapter.save(unsent(UUID.randomUUID()));

    var claimed = adapter.claim(new ClaimRequest(ORIGIN, "w1", 10, TTL));

    assertThat(claimed).singleElement().extracting(OutboxMessage::traceparent).isNull();
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
        .occurredAt(Instant.now())
        .build();
  }
}
