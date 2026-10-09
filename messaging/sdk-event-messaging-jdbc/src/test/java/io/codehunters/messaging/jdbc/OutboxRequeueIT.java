package io.codehunters.messaging.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.ClaimRequest;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.OutboxStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Operators put FAILED rows of the store's origin back to PENDING, by id or by message key. */
class OutboxRequeueIT {

  private static final String ORIGIN = "sentinel-ms-test";
  private static final String OTHER_ORIGIN = "other-ms";
  private static final String TOPIC = "t-main";
  private static final Duration TTL = Duration.ofMinutes(5);
  private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
  private static final Instant FAILED_AT = NOW.minus(Duration.ofHours(1));
  private static final int EXHAUSTED_ATTEMPTS = 5;

  private final AtomicInteger dispatchRequests = new AtomicInteger();
  private final AtomicInteger requestsBeforeCommit = new AtomicInteger(-1);
  private JdbcClient jdbcClient;
  private TransactionTemplate transaction;
  private OutboxJdbcAdapter adapter;
  private OutboxJdbcAdapter otherOriginAdapter;

  @BeforeEach
  void setUp() {
    DataSource dataSource = PostgresTestSupport.dataSource();
    PostgresTestSupport.truncate();
    jdbcClient = JdbcClient.create(dataSource);
    transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    adapter = adapter(dataSource, ORIGIN);
    otherOriginAdapter = adapter(dataSource, OTHER_ORIGIN);
  }

  @Test
  void should_return_pending_with_reset_attempts_when_failed_row_is_requeued() {
    UUID failed = saveFailed(adapter, "A", "A1");

    int requeued = adapter.requeue(List.of(failed));

    OutboxMessage<String> row = row(failed);
    assertThat(requeued).isEqualTo(1);
    assertThat(row.status()).isEqualTo(OutboxStatus.PENDING);
    assertThat(row.attempts()).isZero();
    assertThat(row.error()).isNull();
    assertThat(row.nextAttemptAt()).isEqualTo(NOW);
  }

  @Test
  void should_clear_claim_and_send_mark_when_failed_row_is_requeued() {
    UUID failed = saveFailed(adapter, "A", "A1");
    markClaimedAndSent(failed);

    adapter.requeue(List.of(failed));

    OutboxMessage<String> row = row(failed);
    assertThat(row.claimedAt()).isNull();
    assertThat(row.claimedBy()).isNull();
    assertThat(row.sendStartedAt()).isNull();
  }

  @Test
  void should_leave_rows_of_other_statuses_untouched_when_their_ids_are_requeued() {
    UUID pending = save(adapter, "A", "A1");
    UUID inFlight = save(adapter, "B", "B1");
    UUID published = save(adapter, "C", "C1");
    claim();
    adapter.update(row(published).published(NOW));
    adapter.update(row(pending).released(NOW));

    int requeued = adapter.requeue(List.of(pending, inFlight, published));

    assertThat(requeued).isZero();
    assertThat(row(pending).status()).isEqualTo(OutboxStatus.PENDING);
    assertThat(row(inFlight).status()).isEqualTo(OutboxStatus.IN_FLIGHT);
    assertThat(row(published).status()).isEqualTo(OutboxStatus.PUBLISHED);
  }

  @Test
  void should_leave_failed_rows_of_other_origins_untouched_when_their_ids_are_requeued() {
    UUID foreign = saveFailed(otherOriginAdapter, "A", "A1");

    int requeued = adapter.requeue(List.of(foreign));

    assertThat(requeued).isZero();
    assertThat(row(foreign).status()).isEqualTo(OutboxStatus.FAILED);
    assertThat(row(foreign).attempts()).isEqualTo(EXHAUSTED_ATTEMPTS);
  }

  @Test
  void should_requeue_only_listed_rows_when_several_rows_failed() {
    UUID listed = saveFailed(adapter, "A", "A1");
    UUID unlisted = saveFailed(adapter, "B", "B1");

    int requeued = adapter.requeue(List.of(listed));

    assertThat(requeued).isEqualTo(1);
    assertThat(row(unlisted).status()).isEqualTo(OutboxStatus.FAILED);
  }

  @Test
  void should_return_zero_when_ids_are_empty() {
    saveFailed(adapter, "A", "A1");
    dispatchRequests.set(0);

    assertThat(adapter.requeue(List.of())).isZero();
    assertThat(dispatchRequests).hasValue(0);
  }

  @Test
  void should_return_zero_when_ids_are_unknown() {
    saveFailed(adapter, "A", "A1");

    assertThat(adapter.requeue(List.of(UUID.randomUUID()))).isZero();
  }

  @Test
  void should_dispatch_key_in_order_when_its_failed_head_is_requeued() {
    UUID head = saveFailed(adapter, "A", "A1");
    save(adapter, "A", "A2");
    save(adapter, "A", "A3");
    assertThat(payloads(claim())).isEmpty();

    adapter.requeue(List.of(head));

    assertThat(payloads(claim())).containsExactly("A1", "A2", "A3");
  }

  @Test
  void should_requeue_failed_rows_of_key_in_every_topic_when_requeued_by_key() {
    UUID main = saveFailed(adapter, "A", "A1");
    UUID audit = saveFailed(adapter, "audit", "A", "A1-audit");

    int requeued = adapter.requeueFailed("A");

    assertThat(requeued).isEqualTo(2);
    assertThat(row(main).status()).isEqualTo(OutboxStatus.PENDING);
    assertThat(row(audit).status()).isEqualTo(OutboxStatus.PENDING);
    assertThat(row(audit).attempts()).isZero();
  }

  @Test
  void should_leave_other_keys_origins_and_statuses_untouched_when_requeued_by_key() {
    UUID otherKey = saveFailed(adapter, "B", "B1");
    UUID foreign = saveFailed(otherOriginAdapter, "A", "A1");
    UUID pending = save(adapter, "A", "A2");

    int requeued = adapter.requeueFailed("A");

    assertThat(requeued).isZero();
    assertThat(row(otherKey).status()).isEqualTo(OutboxStatus.FAILED);
    assertThat(row(foreign).status()).isEqualTo(OutboxStatus.FAILED);
    assertThat(row(pending).attempts()).isZero();
  }

  @Test
  void should_request_dispatch_after_commit_when_rows_are_requeued_in_transaction() {
    UUID failed = saveFailed(adapter, "A", "A1");
    dispatchRequests.set(0);

    transaction.executeWithoutResult(
        status -> {
          adapter.requeue(List.of(failed));
          requestsBeforeCommit.set(dispatchRequests.get());
        });

    assertThat(requestsBeforeCommit).hasValue(0);
    assertThat(dispatchRequests).hasValue(1);
  }

  @Test
  void should_request_dispatch_after_commit_when_key_is_requeued_in_transaction() {
    saveFailed(adapter, "A", "A1");
    dispatchRequests.set(0);

    transaction.executeWithoutResult(status -> adapter.requeueFailed("A"));

    assertThat(dispatchRequests).hasValue(1);
  }

  @Test
  void should_request_dispatch_right_away_when_requeued_without_transaction() {
    UUID failed = saveFailed(adapter, "A", "A1");
    dispatchRequests.set(0);

    adapter.requeue(List.of(failed));

    assertThat(dispatchRequests).hasValue(1);
  }

  @Test
  void should_not_request_dispatch_when_nothing_is_requeued() {
    saveFailed(adapter, "A", "A1");
    dispatchRequests.set(0);

    transaction.executeWithoutResult(
        status -> {
          adapter.requeue(List.of(UUID.randomUUID()));
          adapter.requeueFailed("unknown");
        });

    assertThat(dispatchRequests).hasValue(0);
  }

  @Test
  void should_not_request_dispatch_when_requeued_transaction_rolls_back() {
    UUID failed = saveFailed(adapter, "A", "A1");
    dispatchRequests.set(0);

    transaction.executeWithoutResult(
        status -> {
          adapter.requeue(List.of(failed));
          status.setRollbackOnly();
        });

    assertThat(dispatchRequests).hasValue(0);
    assertThat(row(failed).status()).isEqualTo(OutboxStatus.FAILED);
  }

  private OutboxJdbcAdapter adapter(DataSource dataSource, String origin) {
    var settings =
        new OutboxAdapterSettings(
            origin, Clock.fixed(NOW, ZoneOffset.UTC), dispatchRequests::incrementAndGet);
    return new OutboxJdbcAdapter(dataSource, JsonMapper.builder().build(), settings);
  }

  private UUID save(OutboxJdbcAdapter target, String key, String payload) {
    return save(target, TOPIC, key, payload);
  }

  private UUID save(OutboxJdbcAdapter target, String topic, String key, String payload) {
    var id = UUID.randomUUID();
    target.save(
        OutboxMessage.<String>builder().id(id).topic(topic).key(key).message(payload).build());
    return id;
  }

  private UUID saveFailed(OutboxJdbcAdapter target, String key, String payload) {
    return saveFailed(target, TOPIC, key, payload);
  }

  private UUID saveFailed(OutboxJdbcAdapter target, String topic, String key, String payload) {
    UUID id = save(target, topic, key, payload);
    target.update(
        row(id).toBuilder()
            .status(OutboxStatus.FAILED)
            .attempts(EXHAUSTED_ATTEMPTS)
            .error("broker down")
            .nextAttemptAt(FAILED_AT)
            .build());
    return id;
  }

  // A FAILED row never keeps its claim through update(), so the columns are written directly to
  // prove the requeue clears them whatever state an older version or an operator left behind.
  private void markClaimedAndSent(UUID id) {
    jdbcClient
        .sql(
            "UPDATE outbox_message SET claimed_at = :at, claimed_by = 'w-old', send_started_at = :at"
                + " WHERE id = :id")
        .param("at", OutboxRows.utc(FAILED_AT))
        .param("id", id)
        .update();
  }

  private List<OutboxMessage<String>> claim() {
    return adapter.claim(new ClaimRequest(ORIGIN, "w1", 10, TTL, 10));
  }

  private OutboxMessage<String> row(UUID id) {
    return jdbcClient
        .sql("SELECT * FROM outbox_message WHERE id = :id")
        .param("id", id)
        .query(OutboxRows.MAPPER)
        .single();
  }

  private static List<String> payloads(List<OutboxMessage<String>> claimed) {
    return claimed.stream().map(OutboxMessage::message).toList();
  }
}
