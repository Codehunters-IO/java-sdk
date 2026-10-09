package io.codehunters.messaging;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxMessageTest {

  @Test
  void should_build_with_pending_status_by_default() {
    var message =
        OutboxMessage.<String>builder()
            .topic("test-topic")
            .key("key-1")
            .message("payload")
            .occurredAt(LocalDateTime.now())
            .build();

    assertEquals(OutboxStatus.PENDING, message.getStatus());
    assertEquals(0, message.getAttempts());
    assertNull(message.getId());
    assertEquals("test-topic", message.getTopic());
  }

  @Test
  void should_build_with_all_fields() {
    var id = UUID.randomUUID();
    var now = LocalDateTime.now();

    var message =
        OutboxMessage.<String>builder()
            .id(id)
            .origin("payment-ms")
            .routingKey("payment.RAFFLE")
            .topic("payment.results")
            .key("pay-123")
            .message("{\"amount\":100}")
            .status(OutboxStatus.PUBLISHED)
            .attempts(2)
            .error("timeout")
            .occurredAt(now)
            .nextAttemptAt(now.plusMinutes(5))
            .publishedAt(now.plusSeconds(10))
            .build();

    assertEquals(id, message.getId());
    assertEquals("payment-ms", message.getOrigin());
    assertEquals("payment.RAFFLE", message.getRoutingKey());
    assertEquals(OutboxStatus.PUBLISHED, message.getStatus());
    assertEquals(2, message.getAttempts());
    assertEquals("timeout", message.getError());
  }

  @Test
  void should_carry_origin_through_builder() {
    var message =
        OutboxMessage.<String>builder()
            .topic("t")
            .origin("payment-ms")
            .message("p")
            .occurredAt(LocalDateTime.now())
            .build();

    assertEquals("payment-ms", message.getOrigin());
    assertNull(message.getRoutingKey());
  }
}
