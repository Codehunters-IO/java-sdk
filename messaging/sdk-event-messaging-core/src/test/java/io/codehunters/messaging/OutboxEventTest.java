package io.codehunters.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OutboxEventTest {

  private static final OutboxEvent<String> PLACED =
      OutboxEvent.to("orders").key("order-1").type("OrderPlaced").payload("{}");

  @Test
  void should_name_every_mandatory_component_when_built_fluently() {
    assertThat(PLACED.topic()).isEqualTo("orders");
    assertThat(PLACED.key()).isEqualTo("order-1");
    assertThat(PLACED.type()).isEqualTo("OrderPlaced");
    assertThat(PLACED.payload()).isEqualTo("{}");
  }

  @Test
  void should_apply_defaults_when_optional_components_are_not_set() {
    assertThat(PLACED.routingKey()).isNull();
    assertThat(PLACED.eventType()).isEqualTo(EventTypes.EVENT);
    assertThat(PLACED.locale()).isNull();
    assertThat(PLACED.traceId()).isNull();
    assertThat(PLACED.schemaVersion()).isEqualTo("1.0.0");
  }

  @Test
  void should_return_new_event_and_keep_original_when_optional_component_is_set() {
    OutboxEvent<String> routed =
        PLACED
            .routingKey("orders.placed")
            .eventType(EventTypes.COMMAND)
            .locale("es")
            .traceId("trace-1")
            .schemaVersion("2.0.0");

    assertThat(routed.routingKey()).isEqualTo("orders.placed");
    assertThat(routed.eventType()).isEqualTo(EventTypes.COMMAND);
    assertThat(routed.locale()).isEqualTo("es");
    assertThat(routed.traceId()).isEqualTo("trace-1");
    assertThat(routed.schemaVersion()).isEqualTo("2.0.0");
    assertThat(PLACED.routingKey()).isNull();
  }

  @Test
  void should_reject_blank_topic_when_started() {
    assertThatThrownBy(() -> OutboxEvent.to(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("topic");
  }

  @Test
  void should_reject_blank_key_when_keyed() {
    assertThatThrownBy(() -> OutboxEvent.to("orders").key(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("key");
  }

  @Test
  void should_reject_null_type_when_typed() {
    assertThatThrownBy(() -> OutboxEvent.to("orders").key("k").type(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("type");
  }

  @Test
  void should_reject_null_payload_when_built() {
    assertThatThrownBy(() -> OutboxEvent.to("orders").key("k").type("T").payload(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("payload");
  }
}
