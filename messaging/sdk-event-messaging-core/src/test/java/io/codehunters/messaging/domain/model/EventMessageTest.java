package io.codehunters.messaging.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EventMessageTest {

  @Test
  void should_build_event_message_with_defaults() {
    var message =
        EventMessage.<String>builder()
            .header(
                EventMessage.Header.builder().traceId("trace-123").origin("vitxo-ms-test").build())
            .body(
                EventMessage.Body.<String>builder()
                    .aggregate("order")
                    .eventName("OrderCreated")
                    .eventType(EventTypes.EVENT)
                    .payload("test-payload")
                    .build())
            .build();

    assertEquals("trace-123", message.getHeader().getTraceId());
    assertEquals("vitxo-ms-test", message.getHeader().getOrigin());
    assertEquals("1.0.0", message.getHeader().getVersion());
    assertEquals("1.0.0", message.getHeader().getSchemaVersion());
    assertNotNull(message.getHeader().getTimestamp());
    assertEquals("order", message.getBody().getAggregate());
    assertEquals("OrderCreated", message.getBody().getEventName());
    assertEquals(EventTypes.EVENT, message.getBody().getEventType());
    assertEquals("test-payload", message.getBody().getPayload());
  }

  @Test
  void should_build_header_with_custom_values() {
    var header =
        EventMessage.Header.builder()
            .traceId("t-1")
            .version("2.0.0")
            .schemaVersion("3.0.0")
            .origin("custom-origin")
            .timestamp("2026-01-01T00:00:00Z")
            .build();

    assertEquals("2.0.0", header.getVersion());
    assertEquals("3.0.0", header.getSchemaVersion());
    assertEquals("2026-01-01T00:00:00Z", header.getTimestamp());
  }

  @Test
  void should_support_generic_payload_type() {
    record TestPayload(String name, int value) {}

    var message =
        EventMessage.<TestPayload>builder()
            .header(EventMessage.Header.builder().traceId("t-1").origin("test").build())
            .body(
                EventMessage.Body.<TestPayload>builder()
                    .aggregate("test")
                    .eventName("TestEvent")
                    .eventType(EventTypes.COMMAND)
                    .payload(new TestPayload("item", 42))
                    .build())
            .build();

    assertEquals("item", message.getBody().getPayload().name());
    assertEquals(42, message.getBody().getPayload().value());
  }
}
