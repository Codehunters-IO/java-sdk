package io.codehunters.messaging.infrastructure.drivenadapters.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.messaging.domain.model.OutboxMessage;
import io.codehunters.messaging.domain.model.OutboxStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import tools.jackson.databind.json.JsonMapper;

class OutboxJdbcAdapterTest {

  private static final String ORIGIN = "vitxo-ms-test";

  private DataSource dataSource;
  private JdbcClient jdbcClient;
  private JsonMapper jsonMapper;
  private OutboxJdbcAdapter adapter;

  @BeforeEach
  void setUp() {
    dataSource =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .addScript("db/test-schema.sql")
            .generateUniqueName(true)
            .build();
    jdbcClient = JdbcClient.create(dataSource);
    jsonMapper = JsonMapper.builder().build();
    adapter = new OutboxJdbcAdapter(dataSource, jsonMapper, ORIGIN);
  }

  @Test
  void should_save_outbox_message_with_origin_and_routing_key() {
    var message =
        OutboxMessage.<String>builder()
            .topic("test-topic")
            .routingKey("payment.RAFFLE")
            .key("key-1")
            .message("{\"data\":1}")
            .occurredAt(LocalDateTime.now())
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
            .occurredAt(LocalDateTime.now())
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
            .origin("vitxo-ms-other")
            .topic("test-topic")
            .key("key-2")
            .message("{\"data\":2}")
            .occurredAt(LocalDateTime.now())
            .build();

    adapter.save(message);

    var origin =
        jdbcClient
            .sql("SELECT origin FROM outbox_message WHERE message_key = 'key-2'")
            .query(String.class)
            .single();
    assertThat(origin).isEqualTo("vitxo-ms-other");
  }

  @Test
  void should_fallback_to_default_origin_when_message_has_none() {
    var message =
        OutboxMessage.<String>builder()
            .topic("test-topic")
            .key("key-default")
            .message("{\"data\":4}")
            .occurredAt(LocalDateTime.now())
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
            .occurredAt(LocalDateTime.now())
            .build();

    adapter.save(message);

    var payload = jdbcClient.sql("SELECT payload FROM outbox_message").query(String.class).single();
    assertThat(payload).contains("\"name\"").contains("\"test\"");
  }

  @Test
  void should_update_outbox_message_status() {
    var id = UUID.randomUUID();
    var now = LocalDateTime.now();

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
            .publishedAt(LocalDateTime.now())
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
  void should_claim_pending_messages_ordered_by_occurred_at() {
    var now = LocalDateTime.now();

    adapter.save(
        OutboxMessage.<String>builder()
            .topic("topic-1")
            .key("k1")
            .message("oldest")
            .occurredAt(now.minusMinutes(10))
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
            .occurredAt(now.minusMinutes(5))
            .build());

    var result = adapter.claimPendingMessages(ORIGIN, 10, "instance-1", Duration.ofMinutes(5));

    assertThat(result).hasSize(3);
    assertThat(result.get(0).getMessage()).isEqualTo("oldest");
    assertThat(result.get(1).getMessage()).isEqualTo("middle");
    assertThat(result.get(2).getMessage()).isEqualTo("newest");
  }

  @Test
  void should_return_empty_list_when_no_pending_messages() {
    var result = adapter.claimPendingMessages(ORIGIN, 10, "instance-1", Duration.ofMinutes(5));

    assertThat(result).isEmpty();
  }

  @Test
  void should_respect_batch_size_on_claim() {
    var now = LocalDateTime.now();
    for (int i = 0; i < 5; i++) {
      adapter.save(
          OutboxMessage.<String>builder()
              .topic("t")
              .key("k" + i)
              .message("m" + i)
              .occurredAt(now.plusSeconds(i))
              .build());
    }

    var result = adapter.claimPendingMessages(ORIGIN, 3, "instance-1", Duration.ofMinutes(5));

    assertThat(result).hasSize(3);
  }

  @Test
  void should_only_claim_pending_messages_not_published() {
    var id = UUID.randomUUID();
    var now = LocalDateTime.now();

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

    var result = adapter.claimPendingMessages(ORIGIN, 10, "instance-1", Duration.ofMinutes(5));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getMessage()).isEqualTo("pending-msg");
  }

  @Test
  void should_filter_pending_messages_by_origin() {
    var now = LocalDateTime.now();
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
            .origin("vitxo-ms-other")
            .key("other")
            .message("other-payload")
            .occurredAt(now)
            .build());

    var result = adapter.claimPendingMessages(ORIGIN, 10, "instance-1", Duration.ofMinutes(5));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getMessage()).isEqualTo("mine-payload");
  }

  @Test
  void should_filter_claim_by_origin() {
    var now = LocalDateTime.now();
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
            .origin("vitxo-ms-other")
            .key("other")
            .message("other-payload")
            .occurredAt(now)
            .build());

    var claimed = adapter.claimPendingMessages(ORIGIN, 10, "instance-1", Duration.ofMinutes(5));

    assertThat(claimed).hasSize(1);
    assertThat(claimed.get(0).getMessage()).isEqualTo("mine-payload");
    assertThat(claimed.get(0).getOrigin()).isEqualTo(ORIGIN);
  }

  @Test
  void should_map_all_fields_from_row() {
    var id = UUID.randomUUID();
    var now = LocalDateTime.of(2026, 4, 27, 12, 0, 0);

    adapter.save(
        OutboxMessage.<String>builder()
            .id(id)
            .origin(ORIGIN)
            .routingKey("custom-rk")
            .topic("events.user")
            .key("user-123")
            .message("{\"name\":\"test\"}")
            .occurredAt(now)
            .nextAttemptAt(now.plusMinutes(5))
            .build());

    var result = adapter.claimPendingMessages(ORIGIN, 10, "instance-1", Duration.ofMinutes(5));

    assertThat(result).hasSize(1);
    var msg = result.get(0);
    assertThat(msg.getId()).isEqualTo(id);
    assertThat(msg.getOrigin()).isEqualTo(ORIGIN);
    assertThat(msg.getRoutingKey()).isEqualTo("custom-rk");
    assertThat(msg.getTopic()).isEqualTo("events.user");
    assertThat(msg.getKey()).isEqualTo("user-123");
    assertThat(msg.getMessage()).isEqualTo("{\"name\":\"test\"}");
    assertThat(msg.getStatus()).isEqualTo(OutboxStatus.IN_FLIGHT);
    assertThat(msg.getAttempts()).isZero();
    assertThat(msg.getOccurredAt()).isEqualTo(now);
    assertThat(msg.getNextAttemptAt()).isEqualTo(now.plusMinutes(5));
  }

  @Test
  void should_persist_blank_routing_key_as_null() {
    adapter.save(
        OutboxMessage.<String>builder()
            .topic("t")
            .routingKey("")
            .key("k")
            .message("data")
            .occurredAt(LocalDateTime.now())
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
            .occurredAt(LocalDateTime.now())
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
            .occurredAt(LocalDateTime.now())
            .build());

    var claimed = adapter.claimPendingMessages(ORIGIN, 10, "instance-1", Duration.ofMinutes(5));

    assertThat(claimed).hasSize(1);
    assertThat(claimed.get(0).getStatus()).isEqualTo(OutboxStatus.IN_FLIGHT);
    assertThat(claimed.get(0).getClaimedBy()).startsWith("instance-1:");
    assertThat(claimed.get(0).getClaimedAt()).isNotNull();
  }

  @Test
  void should_not_re_claim_in_flight_messages_within_ttl() {
    adapter.save(
        OutboxMessage.<String>builder()
            .topic("t")
            .key("k")
            .message("payload")
            .occurredAt(LocalDateTime.now())
            .build());

    var firstClaim = adapter.claimPendingMessages(ORIGIN, 10, "instance-1", Duration.ofMinutes(5));
    assertThat(firstClaim).hasSize(1);

    var secondClaim = adapter.claimPendingMessages(ORIGIN, 10, "instance-2", Duration.ofMinutes(5));

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
            .occurredAt(LocalDateTime.now())
            .build());

    var oldClaimAt = LocalDateTime.now().minusMinutes(10);
    jdbcClient
        .sql(
            "UPDATE outbox_message SET status='IN_FLIGHT', claimed_at=:claimedAt, claimed_by='dead-instance' WHERE id=:id")
        .param("claimedAt", oldClaimAt)
        .param("id", id)
        .update();

    var reclaimed =
        adapter.claimPendingMessages(ORIGIN, 10, "live-instance", Duration.ofMinutes(5));

    assertThat(reclaimed).hasSize(1);
    assertThat(reclaimed.get(0).getClaimedBy()).startsWith("live-instance:");
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
            .occurredAt(LocalDateTime.now())
            .nextAttemptAt(LocalDateTime.now().plusHours(1))
            .build());

    var claimed = adapter.claimPendingMessages(ORIGIN, 10, "instance-1", Duration.ofMinutes(5));

    assertThat(claimed).isEmpty();
  }

  @Test
  void should_claim_pending_messages_with_past_next_attempt() {
    adapter.save(
        OutboxMessage.<String>builder()
            .topic("t")
            .key("k")
            .message("payload")
            .occurredAt(LocalDateTime.now())
            .nextAttemptAt(LocalDateTime.now().minusMinutes(1))
            .build());

    var claimed = adapter.claimPendingMessages(ORIGIN, 10, "instance-1", Duration.ofMinutes(5));

    assertThat(claimed).hasSize(1);
  }

  @Test
  void should_persist_claim_columns_on_update() {
    var id = UUID.randomUUID();
    var now = LocalDateTime.now();
    adapter.save(
        OutboxMessage.<String>builder()
            .id(id)
            .topic("t")
            .key("k")
            .message("p")
            .occurredAt(now)
            .build());

    var updated =
        OutboxMessage.<String>builder()
            .id(id)
            .topic("t")
            .key("k")
            .message("p")
            .status(OutboxStatus.IN_FLIGHT)
            .attempts(0)
            .claimedAt(now)
            .claimedBy("instance-x")
            .build();
    adapter.update(updated);

    var claimedBy =
        jdbcClient
            .sql("SELECT claimed_by FROM outbox_message WHERE id=:id")
            .param("id", id)
            .query(String.class)
            .single();
    assertThat(claimedBy).isEqualTo("instance-x");
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
            .occurredAt(LocalDateTime.now())
            .build();

    assertThatThrownBy(() -> adapterWithBrokenMapper.save(message))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to serialize outbox message payload");
  }
}
