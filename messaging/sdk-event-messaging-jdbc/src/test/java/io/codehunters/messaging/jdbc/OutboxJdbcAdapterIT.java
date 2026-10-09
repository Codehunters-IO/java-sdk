package io.codehunters.messaging.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.codehunters.messaging.ClaimRequest;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.OutboxStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class OutboxJdbcAdapterIT {

  private static final String ORIGIN = "orders-ms-test";

  private DataSource dataSource;
  private JdbcClient jdbcClient;
  private JsonMapper jsonMapper;
  private OutboxJdbcAdapter adapter;
  private ListAppender<ILoggingEvent> adapterLogs;

  @BeforeEach
  void setUp() {
    adapterLogs = new ListAppender<>();
    adapterLogs.start();
    adapterLogger().addAppender(adapterLogs);
    dataSource = PostgresTestSupport.dataSource();
    PostgresTestSupport.truncate();
    jdbcClient = JdbcClient.create(dataSource);
    jsonMapper = JsonMapper.builder().build();
    adapter = new OutboxJdbcAdapter(dataSource, jsonMapper, ORIGIN);
  }

  @AfterEach
  void tearDown() {
    adapterLogger().detachAppender(adapterLogs);
  }

  private static Logger adapterLogger() {
    return (Logger) LoggerFactory.getLogger(OutboxJdbcAdapter.class);
  }

  @Test
  void should_save_outbox_message_with_origin_and_routing_key() {
    var message =
        OutboxMessage.<String>builder()
            .topic("test-topic")
            .routingKey("payment.RAFFLE")
            .key("key-1")
            .message("{\"data\":1}")
            .occurredAt(Instant.now())
            .build();

    adapter.save(message);

    var saved =
        jdbcClient
            .sql("SELECT * FROM outbox_message")
            .query(
                (rs, rowNum) -> {
                  assertThat(rs.getString("origin")).isEqualTo(ORIGIN);
                  assertThat(rs.getString("routing_key")).isEqualTo("payment.RAFFLE");
                  assertThat(rs.getString("topic")).isEqualTo("test-topic");
                  assertThat(rs.getString("payload")).isEqualTo("{\"data\":1}");
                  assertThat(rs.getString("status")).isEqualTo("PENDING");
                  return true;
                })
            .single();
    assertThat(saved).isTrue();
  }

  @Test
  void should_persist_routing_key_as_null_when_message_has_none() {
    var message =
        OutboxMessage.<String>builder()
            .topic("test-topic")
            .key("key-3")
            .message("{\"data\":3}")
            .occurredAt(Instant.now())
            .build();

    adapter.save(message);

    var routingKey =
        jdbcClient
            .sql("SELECT routing_key FROM outbox_message WHERE message_key = 'key-3'")
            .query((rs, rowNum) -> rs.getString("routing_key"))
            .optional();
    assertThat(routingKey).isEmpty();
  }

  @Test
  void should_use_message_origin_when_provided() {
    var message =
        OutboxMessage.<String>builder()
            .origin("orders-ms-other")
            .topic("test-topic")
            .key("key-2")
            .message("{\"data\":2}")
            .occurredAt(Instant.now())
            .build();

    adapter.save(message);

    var origin =
        jdbcClient
            .sql("SELECT origin FROM outbox_message WHERE message_key = 'key-2'")
            .query(String.class)
            .single();
    assertThat(origin).isEqualTo("orders-ms-other");
  }

  @Test
  void should_fallback_to_default_origin_when_message_has_none() {
    var message =
        OutboxMessage.<String>builder()
            .topic("test-topic")
            .key("key-default")
            .message("{\"data\":4}")
            .occurredAt(Instant.now())
            .build();

    adapter.save(message);

    var origin =
        jdbcClient
            .sql("SELECT origin FROM outbox_message WHERE message_key = 'key-default'")
            .query(String.class)
            .single();
    assertThat(origin).isEqualTo(ORIGIN);
  }

  @Test
  void should_serialize_non_string_payload_to_json() {
    record TestPayload(String name) {}

    var message =
        OutboxMessage.<TestPayload>builder()
            .topic("test-topic")
            .key("key-1")
            .message(new TestPayload("test"))
            .occurredAt(Instant.now())
            .build();

    adapter.save(message);

    var payload = jdbcClient.sql("SELECT payload FROM outbox_message").query(String.class).single();
    assertThat(payload).contains("\"name\"").contains("\"test\"");
  }

  @Test
  void should_update_outbox_message_status() {
    var id = UUID.randomUUID();
    var now = Instant.now();

    var message =
        OutboxMessage.<String>builder()
            .id(id)
            .topic("t")
            .key("k")
            .message("p")
            .occurredAt(now)
            .build();
    adapter.save(message);

    var updated =
        OutboxMessage.<String>builder()
            .id(id)
            .topic("t")
            .key("k")
            .message("p")
            .status(OutboxStatus.PUBLISHED)
            .attempts(1)
            .publishedAt(Instant.now())
            .build();
    adapter.update(updated);

    var status =
        jdbcClient
            .sql("SELECT status FROM outbox_message WHERE id = :id")
            .param("id", id)
            .query(String.class)
            .single();
    assertThat(status).isEqualTo("PUBLISHED");
  }

  @Test
  void should_claim_pending_messages_in_insertion_order_regardless_of_occurred_at() {
    var now = Instant.now();

    adapter.save(
        OutboxMessage.<String>builder()
            .topic("topic-1")
            .key("k1")
            .message("oldest")
            .occurredAt(now.minus(Duration.ofMinutes(10)))
            .build());

    adapter.save(
        OutboxMessage.<String>builder()
            .topic("topic-2")
            .key("k2")
            .message("newest")
            .occurredAt(now)
            .build());

    adapter.save(
        OutboxMessage.<String>builder()
            .topic("topic-3")
            .key("k3")
            .message("middle")
            .occurredAt(now.minus(Duration.ofMinutes(5)))
            .build());

    var result = adapter.claim(claimOf(10, "instance-1"));

    assertThat(result)
        .extracting(OutboxMessage::message)
        .containsExactly("oldest", "newest", "middle");
  }

  @Test
  void should_return_empty_list_when_no_pending_messages() {
    var result = adapter.claim(claimOf(10, "instance-1"));

    assertThat(result).isEmpty();
  }

  @Test
  void should_respect_batch_size_on_claim() {
    var now = Instant.now();
    for (int i = 0; i < 5; i++) {
      adapter.save(
          OutboxMessage.<String>builder()
              .topic("t")
              .key("k" + i)
              .message("m" + i)
              .occurredAt(now.plusSeconds(i))
              .build());
    }

    var result = adapter.claim(claimOf(3, "instance-1"));

    assertThat(result).hasSize(3);
  }

  @Test
  void should_only_claim_pending_messages_not_published() {
    var id = UUID.randomUUID();
    var now = Instant.now();

    adapter.save(
        OutboxMessage.<String>builder()
            .id(id)
            .topic("t")
            .key("k")
            .message("published-msg")
            .occurredAt(now)
            .build());

    adapter.update(
        OutboxMessage.<String>builder()
            .id(id)
            .topic("t")
            .key("k")
            .message("published-msg")
            .status(OutboxStatus.PUBLISHED)
            .attempts(1)
            .publishedAt(now)
            .build());

    adapter.save(
        OutboxMessage.<String>builder()
            .topic("t")
            .key("k2")
            .message("pending-msg")
            .occurredAt(now)
            .build());

    var result = adapter.claim(claimOf(10, "instance-1"));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).message()).isEqualTo("pending-msg");
  }

  @Test
  void should_filter_pending_messages_by_origin() {
    var now = Instant.now();
    adapter.save(
        OutboxMessage.<String>builder()
            .topic("t")
            .origin(ORIGIN)
            .key("mine")
            .message("mine-payload")
            .occurredAt(now)
            .build());
    adapter.save(
        OutboxMessage.<String>builder()
            .topic("t")
            .origin("orders-ms-other")
            .key("other")
            .message("other-payload")
            .occurredAt(now)
            .build());

    var result = adapter.claim(claimOf(10, "instance-1"));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).message()).isEqualTo("mine-payload");
  }

  @Test
  void should_filter_claim_by_origin() {
    var now = Instant.now();
    adapter.save(
        OutboxMessage.<String>builder()
            .topic("t")
            .origin(ORIGIN)
            .key("mine")
            .message("mine-payload")
            .occurredAt(now)
            .build());
    adapter.save(
        OutboxMessage.<String>builder()
            .topic("t")
            .origin("orders-ms-other")
            .key("other")
            .message("other-payload")
            .occurredAt(now)
            .build());

    var claimed = adapter.claim(claimOf(10, "instance-1"));

    assertThat(claimed).hasSize(1);
    assertThat(claimed.get(0).message()).isEqualTo("mine-payload");
    assertThat(claimed.get(0).origin()).isEqualTo(ORIGIN);
  }

  @Test
  void should_map_all_fields_from_row() {
    var id = UUID.randomUUID();
    var now = Instant.parse("2026-04-27T12:00:00Z");

    adapter.save(
        OutboxMessage.<String>builder()
            .id(id)
            .origin(ORIGIN)
            .routingKey("custom-rk")
            .topic("events.user")
            .key("user-123")
            .message("{\"name\":\"test\"}")
            .occurredAt(now)
            .nextAttemptAt(now.plus(Duration.ofMinutes(5)))
            .build());

    var result = adapter.claim(claimOf(10, "instance-1"));

    assertThat(result).hasSize(1);
    var msg = result.get(0);
    assertThat(msg.id()).isEqualTo(id);
    assertThat(msg.origin()).isEqualTo(ORIGIN);
    assertThat(msg.routingKey()).isEqualTo("custom-rk");
    assertThat(msg.topic()).isEqualTo("events.user");
    assertThat(msg.key()).isEqualTo("user-123");
    assertThat(msg.message()).isEqualTo("{\"name\":\"test\"}");
    assertThat(msg.status()).isEqualTo(OutboxStatus.IN_FLIGHT);
    assertThat(msg.attempts()).isZero();
    assertThat(msg.occurredAt()).isEqualTo(now);
    assertThat(msg.nextAttemptAt()).isEqualTo(now.plus(Duration.ofMinutes(5)));
  }

  @Test
  void should_persist_blank_routing_key_as_null() {
    adapter.save(
        OutboxMessage.<String>builder()
            .topic("t")
            .routingKey("")
            .key("k")
            .message("data")
            .occurredAt(Instant.now())
            .build());

    var routingKey =
        jdbcClient
            .sql("SELECT routing_key FROM outbox_message WHERE message_key = 'k'")
            .query(String.class)
            .single();
    assertThat(routingKey).isEmpty();
  }

  @Test
  void should_save_with_explicit_id() {
    var explicitId = UUID.randomUUID();

    adapter.save(
        OutboxMessage.<String>builder()
            .id(explicitId)
            .topic("t")
            .key("k")
            .message("data")
            .occurredAt(Instant.now())
            .build());

    var savedId = jdbcClient.sql("SELECT id FROM outbox_message").query(UUID.class).single();
    assertThat(savedId).isEqualTo(explicitId);
  }

  @Test
  void should_claim_pending_messages_and_set_in_flight_status() {
    adapter.save(
        OutboxMessage.<String>builder()
            .topic("t")
            .key("k")
            .message("payload")
            .occurredAt(Instant.now())
            .build());

    var claimed = adapter.claim(claimOf(10, "instance-1"));

    assertThat(claimed).hasSize(1);
    assertThat(claimed.get(0).status()).isEqualTo(OutboxStatus.IN_FLIGHT);
    assertThat(claimed.get(0).claimedBy()).startsWith("instance-1:");
    assertThat(claimed.get(0).claimedAt()).isNotNull();
  }

  @Test
  void should_not_re_claim_in_flight_messages_within_ttl() {
    adapter.save(
        OutboxMessage.<String>builder()
            .topic("t")
            .key("k")
            .message("payload")
            .occurredAt(Instant.now())
            .build());

    var firstClaim = adapter.claim(claimOf(10, "instance-1"));
    assertThat(firstClaim).hasSize(1);

    var secondClaim = adapter.claim(claimOf(10, "instance-2"));

    assertThat(secondClaim).isEmpty();
  }

  @Test
  void should_re_claim_in_flight_messages_past_ttl() {
    var id = UUID.randomUUID();
    adapter.save(
        OutboxMessage.<String>builder()
            .id(id)
            .topic("t")
            .key("k")
            .message("payload")
            .occurredAt(Instant.now())
            .build());

    var oldClaimAt = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10);
    jdbcClient
        .sql(
            "UPDATE outbox_message SET status='IN_FLIGHT', claimed_at=:claimedAt, claimed_by='dead-instance' WHERE id=:id")
        .param("claimedAt", oldClaimAt)
        .param("id", id)
        .update();

    var reclaimed = adapter.claim(claimOf(10, "live-instance"));

    assertThat(reclaimed).hasSize(1);
    assertThat(reclaimed.get(0).claimedBy()).startsWith("live-instance:");
  }

  @Test
  void should_skip_pending_messages_with_future_next_attempt() {
    var id = UUID.randomUUID();
    adapter.save(
        OutboxMessage.<String>builder()
            .id(id)
            .topic("t")
            .key("k")
            .message("payload")
            .occurredAt(Instant.now())
            .nextAttemptAt(Instant.now().plus(Duration.ofHours(1)))
            .build());

    var claimed = adapter.claim(claimOf(10, "instance-1"));

    assertThat(claimed).isEmpty();
  }

  @Test
  void should_claim_pending_messages_with_past_next_attempt() {
    adapter.save(
        OutboxMessage.<String>builder()
            .topic("t")
            .key("k")
            .message("payload")
            .occurredAt(Instant.now())
            .nextAttemptAt(Instant.now().minus(Duration.ofMinutes(1)))
            .build());

    var claimed = adapter.claim(claimOf(10, "instance-1"));

    assertThat(claimed).hasSize(1);
  }

  @Test
  void should_keep_claim_columns_when_updated_in_flight_by_claim_owner() {
    adapter.save(OutboxMessage.<String>builder().topic("t").key("k").message("p").build());
    var claimed = adapter.claim(claimOf(10, "instance-x")).get(0);

    adapter.update(claimed.toBuilder().attempts(1).build());

    var claimedBy =
        jdbcClient
            .sql("SELECT claimed_by FROM outbox_message WHERE id=:id")
            .param("id", claimed.id())
            .query(String.class)
            .single();
    assertThat(claimedBy).isEqualTo(claimed.claimedBy());
  }

  @Test
  void should_ignore_update_when_claim_marker_does_not_match() {
    adapter.save(OutboxMessage.<String>builder().topic("t").key("k").message("p").build());
    var claimed = adapter.claim(claimOf(10, "instance-x")).get(0);

    adapter.update(
        claimed.toBuilder()
            .claimedBy("instance-stale:marker")
            .status(OutboxStatus.PUBLISHED)
            .build());

    var status =
        jdbcClient
            .sql("SELECT status FROM outbox_message WHERE id=:id")
            .param("id", claimed.id())
            .query(String.class)
            .single();
    assertThat(status).isEqualTo("IN_FLIGHT");
  }

  @Test
  void should_hold_later_same_key_row_when_earlier_row_is_pending() {
    adapter.save(OutboxMessage.<String>builder().topic("t").key("A").message("A1").build());
    adapter.save(OutboxMessage.<String>builder().topic("t").key("A").message("A2").build());
    adapter.save(OutboxMessage.<String>builder().topic("t").key("B").message("B1").build());

    var claimed = adapter.claim(claimOf(10, "instance-1"));

    assertThat(claimed).extracting(OutboxMessage::message).containsExactly("A1", "B1");
  }

  @Test
  void should_throw_when_payload_serialization_fails() {
    var brokenMapper = JsonMapper.builder().build();

    var adapterWithBrokenMapper = new OutboxJdbcAdapter(dataSource, brokenMapper, ORIGIN);

    var unserializable =
        new Object() {
          @SuppressWarnings("unused")
          public Object getSelf() {
            return this;
          }
        };

    var message =
        OutboxMessage.builder()
            .topic("t")
            .key("k")
            .message(unserializable)
            .occurredAt(Instant.now())
            .build();

    assertThatThrownBy(() -> adapterWithBrokenMapper.save(message))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to serialize outbox message payload");
  }

  @Test
  void should_increment_attempts_when_reclaiming_stale_row_whose_send_had_started() {
    UUID id = UUID.randomUUID();
    adapter.save(unsent(id));
    recordOneFailedAttempt(id);
    markStaleInFlight(id);
    markSendStartedByDeadInstance(id);

    var reclaimed = adapter.claim(claimOf(10, "live"));

    assertThat(reclaimed).singleElement().extracting(OutboxMessage::attempts).isEqualTo(2);
    assertThat(attemptsOf(id)).isEqualTo(2);
  }

  @Test
  void should_keep_attempts_when_claiming_pending_row() {
    UUID id = UUID.randomUUID();
    adapter.save(unsent(id));
    recordOneFailedAttempt(id);

    var claimed = adapter.claim(claimOf(10, "live"));

    assertThat(claimed).singleElement().extracting(OutboxMessage::attempts).isEqualTo(1);
    assertThat(attemptsOf(id)).isEqualTo(1);
  }

  @Test
  void should_keep_attempts_when_reclaiming_stale_row_that_was_never_sent() {
    UUID id = UUID.randomUUID();
    adapter.save(unsent(id));
    recordOneFailedAttempt(id);
    markStaleInFlight(id);

    var reclaimed = adapter.claim(claimOf(10, "live"));

    assertThat(reclaimed).singleElement().extracting(OutboxMessage::attempts).isEqualTo(1);
    assertThat(attemptsOf(id)).isEqualTo(1);
  }

  @Test
  void should_clear_send_mark_when_row_is_reclaimed() {
    UUID id = UUID.randomUUID();
    adapter.save(unsent(id));
    markStaleInFlight(id);
    markSendStartedByDeadInstance(id);

    var reclaimed = adapter.claim(claimOf(10, "live"));

    assertThat(reclaimed).singleElement().extracting(OutboxMessage::sendStartedAt).isNull();
    assertThat(sendStartedAtOf(id)).isNull();
  }

  @Test
  void should_stamp_send_mark_when_claim_is_still_held() {
    UUID id = UUID.randomUUID();
    adapter.save(unsent(id));
    var claimed = adapter.claim(claimOf(10, "w1")).get(0);

    var marked = adapter.markSendStarted(claimed);

    assertThat(marked).hasValueSatisfying(m -> assertThat(m.sendStartedAt()).isNotNull());
    assertThat(sendStartedAtOf(id)).isNotNull();
  }

  @Test
  void should_refuse_send_mark_when_claim_was_taken_over() {
    UUID id = UUID.randomUUID();
    adapter.save(unsent(id));
    var stale = adapter.claim(claimOf(10, "w1")).get(0);
    markStaleInFlight(id);

    var marked = adapter.markSendStarted(stale);

    assertThat(marked).isEmpty();
    assertThat(sendStartedAtOf(id)).isNull();
  }

  @Test
  void should_clear_send_mark_when_update_ends_the_claim() {
    UUID id = UUID.randomUUID();
    adapter.save(unsent(id));
    var claimed = adapter.claim(claimOf(10, "w1")).get(0);
    var marked = adapter.markSendStarted(claimed).orElseThrow();

    adapter.update(marked.published(Instant.now()));

    assertThat(sendStartedAtOf(id)).isNull();
  }

  @Test
  void should_warn_once_when_send_mark_runs_inside_caller_transaction() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    adapter.save(unsent(first));
    adapter.save(unsent(second, "other-key"));
    var claimed = adapter.claim(claimOf(10, "w1"));

    inCallerTransaction(
        () -> {
          adapter.markSendStarted(claimed.get(0));
          adapter.markSendStarted(claimed.get(1));
        });

    assertThat(adapterLogs.list)
        .filteredOn(event -> event.getLevel() == Level.WARN)
        .singleElement()
        .extracting(ILoggingEvent::getFormattedMessage)
        .asString()
        .contains("transaction");
  }

  @Test
  void should_not_warn_when_send_mark_runs_outside_a_transaction() {
    UUID id = UUID.randomUUID();
    adapter.save(unsent(id));
    var claimed = adapter.claim(claimOf(10, "w1")).get(0);

    adapter.markSendStarted(claimed);

    assertThat(adapterLogs.list).noneMatch(event -> event.getLevel() == Level.WARN);
  }

  private static ClaimRequest claimOf(int batchSize, String claimedBy) {
    return new ClaimRequest(ORIGIN, claimedBy, batchSize, Duration.ofMinutes(5));
  }

  private void inCallerTransaction(Runnable work) {
    new TransactionTemplate(new DataSourceTransactionManager(dataSource))
        .executeWithoutResult(status -> work.run());
  }

  private void markSendStartedByDeadInstance(UUID id) {
    jdbcClient
        .sql("UPDATE outbox_message SET send_started_at = :old WHERE id = :id")
        .param("old", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(9))
        .param("id", id)
        .update();
  }

  private Object sendStartedAtOf(UUID id) {
    return jdbcClient
        .sql("SELECT send_started_at FROM outbox_message WHERE id = :id")
        .param("id", id)
        .query()
        .singleRow()
        .get("send_started_at");
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
    return unsent(id, "k");
  }

  private static OutboxMessage<String> unsent(UUID id, String key) {
    return OutboxMessage.<String>builder()
        .id(id)
        .topic("t")
        .key(key)
        .message("p")
        .occurredAt(Instant.now())
        .build();
  }
}
