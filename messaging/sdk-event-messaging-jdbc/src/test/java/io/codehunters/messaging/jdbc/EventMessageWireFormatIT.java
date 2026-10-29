package io.codehunters.messaging.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.OutboxEvent;
import io.codehunters.messaging.factory.EventMessageFactory;
import io.codehunters.messaging.factory.OutboxEventFactory;
import io.codehunters.messaging.factory.TraceIdSource;
import io.codehunters.messaging.jdbc.config.EventMessagingJdbcAutoConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The stored payload of an outbox event is the broker wire format consumers parse: a {@code
 * header}/{@code body} envelope with the timestamp as an ISO-8601 string.
 */
class EventMessageWireFormatIT {

  private static final String ORIGIN = "orders-ms";
  private static final Instant NOW = Instant.parse("2026-10-09T10:15:30.123Z");
  private static final OutboxEvent<Placed> PLACED =
      OutboxEvent.to("orders")
          .key("order-7")
          .type("OrderPlaced")
          .payload(new Placed("order-7", 42))
          .locale("es-CO")
          .traceId("4bf92f3577b34da6a3ce929d0e0e4736");

  private final JsonMapper outboxJsonMapper =
      new EventMessagingJdbcAutoConfiguration()
          .outboxJsonMapper(
              new StaticListableBeanFactory().getBeanProvider(JsonMapper.Builder.class));
  private final OutboxEventFactory factory =
      new OutboxEventFactory(
          new EventMessageFactory(ORIGIN, TraceIdSource.none(), Clock.fixed(NOW, ZoneOffset.UTC)));
  private JdbcClient jdbcClient;
  private OutboxJdbcAdapter adapter;

  record Placed(String orderId, int amount) {}

  @BeforeEach
  void setUp() {
    DataSource dataSource = PostgresTestSupport.dataSource();
    PostgresTestSupport.truncate();
    jdbcClient = JdbcClient.create(dataSource);
    adapter = new OutboxJdbcAdapter(dataSource, outboxJsonMapper, ORIGIN);
  }

  @Test
  void should_store_header_with_iso_timestamp_when_event_is_saved() {
    adapter.save(factory.create(PLACED));

    JsonNode header = storedPayload().get("header");

    assertThat(header.propertyNames())
        .containsExactlyInAnyOrder(
            "traceId",
            "version",
            "schemaVersion",
            "origin",
            "timestamp",
            "locale",
            "eventId",
            "idempotencyKey");
    assertThat(header.get("timestamp").isString()).isTrue();
    assertThat(header.get("timestamp").asString()).isEqualTo("2026-10-09T10:15:30.123Z");
    assertThat(header.get("traceId").asString()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    assertThat(header.get("version").asString()).isEqualTo("1.0.0");
    assertThat(header.get("schemaVersion").asString()).isEqualTo("1.0.0");
    assertThat(header.get("origin").asString()).isEqualTo(ORIGIN);
    assertThat(header.get("locale").asString()).isEqualTo("es-CO");
  }

  @Test
  void should_store_row_id_as_event_id_and_null_idempotency_key_when_event_is_saved() {
    var outbox = factory.create(PLACED);
    adapter.save(outbox);

    JsonNode header = storedPayload().get("header");

    assertThat(header.get("eventId").asString()).isEqualTo(outbox.id().toString());
    assertThat(header.get("idempotencyKey").isNull()).isTrue();
  }

  @Test
  void should_store_idempotency_key_when_event_carries_one() {
    adapter.save(factory.create(PLACED.idempotencyKey("order-7-placed")));

    assertThat(storedPayload().get("header").get("idempotencyKey").asString())
        .isEqualTo("order-7-placed");
  }

  @Test
  void should_store_body_with_aggregate_name_type_and_payload_when_event_is_saved() {
    adapter.save(factory.create(PLACED));

    JsonNode body = storedPayload().get("body");

    assertThat(body.propertyNames())
        .containsExactlyInAnyOrder("aggregate", "eventName", "eventType", "payload");
    assertThat(body.get("aggregate").asString()).isEqualTo("order-7");
    assertThat(body.get("eventName").asString()).isEqualTo("OrderPlaced");
    assertThat(body.get("eventType").asString()).isEqualTo("EVENT");
    assertThat(body.get("payload").get("amount").asInt()).isEqualTo(42);
  }

  @Test
  void should_store_only_header_and_body_at_top_level_when_event_is_saved() {
    adapter.save(factory.create(PLACED));

    assertThat(storedPayload().propertyNames()).containsExactlyInAnyOrder("header", "body");
  }

  private JsonNode storedPayload() {
    String payload =
        jdbcClient.sql("SELECT payload FROM outbox_message").query(String.class).single();
    return outboxJsonMapper.readTree(payload);
  }
}
