package io.codehunters.messaging.domain.factory;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.domain.model.EventMessage;
import io.codehunters.messaging.domain.model.OutboxMessage;
import io.codehunters.messaging.domain.model.OutboxStatus;
import org.junit.jupiter.api.Test;

class OutboxEventFactoryTest {

  private static final String ORIGIN = "vitxo-ms-test";

  private final OutboxEventFactory factory =
      new OutboxEventFactory(ORIGIN, new EventMessageFactory(ORIGIN));

  @Test
  void should_wrap_event_message_with_outbox_metadata() {
    OutboxMessage<EventMessage<String>> outbox =
        factory.createRoutingKey(
            "events.user", "user.created", "trace-1", "user-42", "USER_CREATED", "USER", "payload");

    assertThat(outbox.getOrigin()).isEqualTo(ORIGIN);
    assertThat(outbox.getTopic()).isEqualTo("events.user");
    assertThat(outbox.getRoutingKey()).isEqualTo("user.created");
    assertThat(outbox.getKey()).isEqualTo("user-42");
    assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(outbox.getMessage().getHeader().getOrigin()).isEqualTo(ORIGIN);
    assertThat(outbox.getMessage().getHeader().getTraceId()).isEqualTo("trace-1");
    assertThat(outbox.getMessage().getBody().getAggregate()).isEqualTo("user-42");
    assertThat(outbox.getMessage().getBody().getEventName()).isEqualTo("USER_CREATED");
    assertThat(outbox.getMessage().getBody().getEventType()).isEqualTo("USER");
    assertThat(outbox.getMessage().getBody().getPayload()).isEqualTo("payload");
  }

  @Test
  void should_create_without_routing_key() {
    OutboxMessage<EventMessage<String>> outbox =
        factory.create("events.user", "trace-1", "user-42", "USER_CREATED", "USER", "payload");

    assertThat(outbox.getRoutingKey()).isNull();
    assertThat(outbox.getTopic()).isEqualTo("events.user");
  }
}
