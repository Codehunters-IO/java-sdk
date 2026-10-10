package io.codehunters.messaging.factory;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.EventMessage;
import io.codehunters.messaging.EventTypes;
import io.codehunters.messaging.OutboxEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EventMessageFactoryTest {

  private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final OutboxEvent<String> PLACED =
      OutboxEvent.to("orders").key("order-1").type("OrderPlaced").payload("p");

  private final EventMessageFactory factory =
      new EventMessageFactory("payment-ms", TraceIdSource.none(), CLOCK);

  @Test
  void should_stamp_origin_versions_and_clock_time_when_event_is_wrapped() {
    EventMessage<String> message = factory.create(PLACED);

    assertThat(message.header().origin()).isEqualTo("payment-ms");
    assertThat(message.header().version()).isEqualTo("1.0.0");
    assertThat(message.header().schemaVersion()).isEqualTo("1.0.0");
    assertThat(message.header().timestamp()).isEqualTo(NOW);
    assertThat(message.header().locale()).isNull();
  }

  @Test
  void should_map_key_type_and_payload_into_body_when_event_is_wrapped() {
    EventMessage<String> message = factory.create(PLACED.eventType(EventTypes.COMMAND));

    assertThat(message.body().aggregate()).isEqualTo("order-1");
    assertThat(message.body().eventName()).isEqualTo("OrderPlaced");
    assertThat(message.body().eventType()).isEqualTo(EventTypes.COMMAND);
    assertThat(message.body().payload()).isEqualTo("p");
  }

  @Test
  void should_carry_explicit_trace_id_locale_and_schema_version_when_set() {
    EventMessage<String> message =
        factory.create(PLACED.traceId("trace-1").locale("es").schemaVersion("2.0.0"));

    assertThat(message.header().traceId()).isEqualTo("trace-1");
    assertThat(message.header().locale()).isEqualTo("es");
    assertThat(message.header().schemaVersion()).isEqualTo("2.0.0");
  }

  @Test
  void should_use_current_trace_id_when_event_carries_none() {
    var traced = new EventMessageFactory("ms", () -> Optional.of("current"), CLOCK);

    assertThat(traced.create(PLACED).header().traceId()).isEqualTo("current");
  }

  @Test
  void should_use_current_trace_id_when_event_trace_id_is_blank() {
    var traced = new EventMessageFactory("ms", () -> Optional.of("current"), CLOCK);

    assertThat(traced.create(PLACED.traceId(" ")).header().traceId()).isEqualTo("current");
  }
}
