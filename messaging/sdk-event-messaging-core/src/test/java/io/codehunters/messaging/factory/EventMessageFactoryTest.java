package io.codehunters.messaging.factory;

import static org.junit.jupiter.api.Assertions.*;

import io.codehunters.messaging.EventMessage;
import io.codehunters.messaging.EventTypes;
import org.junit.jupiter.api.Test;

class EventMessageFactoryTest {

  private final EventMessageFactory factory = new EventMessageFactory("vitxo-ms-payment");

  @Test
  void should_create_event_message_with_injected_origin() {
    var message =
        factory.create("trace-1", "payment", "PaymentCreated", EventTypes.EVENT, "test-payload");

    assertEquals("vitxo-ms-payment", message.getHeader().getOrigin());
    assertEquals("trace-1", message.getHeader().getTraceId());
    assertEquals("1.0.0", message.getHeader().getVersion());
    assertNotNull(message.getHeader().getTimestamp());
    assertEquals("payment", message.getBody().getAggregate());
    assertEquals("PaymentCreated", message.getBody().getEventName());
    assertEquals(EventTypes.EVENT, message.getBody().getEventType());
    assertEquals("test-payload", message.getBody().getPayload());
  }

  @Test
  void should_create_event_message_with_generic_payload() {
    record OrderPayload(String orderId, int amount) {}

    var payload = new OrderPayload("ord-1", 500);
    EventMessage<OrderPayload> message =
        factory.create("trace-2", "order", "OrderPlaced", EventTypes.COMMAND, payload);

    assertEquals("vitxo-ms-payment", message.getHeader().getOrigin());
    assertEquals("ord-1", message.getBody().getPayload().orderId());
    assertEquals(500, message.getBody().getPayload().amount());
  }

  @Test
  void should_use_different_origin_per_instance() {
    var authFactory = new EventMessageFactory("vitxo-ms-auth");

    var message = authFactory.create("t-1", "user", "UserCreated", EventTypes.EVENT, "data");

    assertEquals("vitxo-ms-auth", message.getHeader().getOrigin());
  }
}
