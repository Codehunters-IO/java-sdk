package io.codehunters.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class EventMessageTest {

  private static final Instant AT = Instant.parse("2026-10-09T10:00:00Z");

  @Test
  void should_expose_header_and_body_components_when_built() {
    var header = new EventMessage.Header("trace-1", "1.0.0", "2.0.0", "orders", AT, "es");
    var body = new EventMessage.Body<>("order-1", "OrderPlaced", EventTypes.EVENT, "payload");

    var message = new EventMessage<>(header, body);

    assertThat(message.header().schemaVersion()).isEqualTo("2.0.0");
    assertThat(message.header().timestamp()).isEqualTo(AT);
    assertThat(message.body().aggregate()).isEqualTo("order-1");
    assertThat(message.body().payload()).isEqualTo("payload");
  }

  @Test
  void should_be_equal_when_components_are_equal() {
    var header = new EventMessage.Header("t", "1.0.0", "1.0.0", "o", AT, null);
    var body = new EventMessage.Body<>("a", "E", EventTypes.COMMAND, 42);

    assertThat(new EventMessage<>(header, body)).isEqualTo(new EventMessage<>(header, body));
  }

  @Test
  void should_default_to_version_one_when_none_is_given() {
    assertThat(EventMessage.Header.DEFAULT_VERSION).isEqualTo("1.0.0");
  }

  @Test
  void should_leave_event_id_and_idempotency_key_null_when_built_with_the_9_2_signature() {
    var header = new EventMessage.Header("t", "1.0.0", "2.0.0", "orders", AT, "es");

    assertThat(header.eventId()).isNull();
    assertThat(header.idempotencyKey()).isNull();
  }

  @Test
  void should_expose_event_id_and_idempotency_key_when_given() {
    var header = new EventMessage.Header("t", "1.0.0", "2.0.0", "orders", AT, "es", "evt-1", "k-1");

    assertThat(header.eventId()).isEqualTo("evt-1");
    assertThat(header.idempotencyKey()).isEqualTo("k-1");
    assertThat(header.locale()).isEqualTo("es");
  }
}
