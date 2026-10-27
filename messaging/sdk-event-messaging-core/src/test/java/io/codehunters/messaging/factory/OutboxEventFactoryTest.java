package io.codehunters.messaging.factory;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.EventMessage;
import io.codehunters.messaging.OutboxEvent;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.OutboxStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OutboxEventFactoryTest {

  private static final String ORIGIN = "orders-ms-test";
  private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
  private static final OutboxEvent<String> CREATED =
      OutboxEvent.to("events.user").key("user-42").type("USER_CREATED").payload("payload");

  private final OutboxEventFactory factory =
      new OutboxEventFactory(
          new EventMessageFactory(ORIGIN, TraceIdSource.none(), Clock.fixed(NOW, ZoneOffset.UTC)));

  @Test
  void should_wrap_event_with_outbox_metadata_when_created() {
    OutboxMessage<EventMessage<String>> outbox = factory.create(CREATED.routingKey("user.created"));

    assertThat(outbox.id()).isNotNull();
    assertThat(outbox.origin()).isEqualTo(ORIGIN);
    assertThat(outbox.topic()).isEqualTo("events.user");
    assertThat(outbox.routingKey()).isEqualTo("user.created");
    assertThat(outbox.key()).isEqualTo("user-42");
    assertThat(outbox.status()).isEqualTo(OutboxStatus.PENDING);
    assertThat(outbox.message().body().aggregate()).isEqualTo("user-42");
    assertThat(outbox.message().body().eventName()).isEqualTo("USER_CREATED");
  }

  @Test
  void should_stamp_occurred_at_and_envelope_time_from_clock_when_created() {
    OutboxMessage<EventMessage<String>> outbox = factory.create(CREATED);

    assertThat(outbox.occurredAt()).isEqualTo(NOW);
    assertThat(outbox.message().header().timestamp()).isEqualTo(NOW);
  }

  @Test
  void should_leave_routing_key_null_when_event_has_none() {
    assertThat(factory.create(CREATED).routingKey()).isNull();
  }

  @Test
  void should_assign_distinct_ids_when_created_twice() {
    assertThat(factory.create(CREATED).id()).isNotEqualTo(factory.create(CREATED).id());
  }

  @Test
  void should_stamp_row_id_as_envelope_event_id_when_created() {
    OutboxMessage<EventMessage<String>> outbox = factory.create(CREATED);

    assertThat(outbox.message().header().eventId()).isEqualTo(outbox.id().toString());
  }

  @Test
  void should_carry_idempotency_key_into_envelope_header_when_event_has_one() {
    OutboxMessage<EventMessage<String>> outbox = factory.create(CREATED.idempotencyKey("k-1"));

    assertThat(outbox.message().header().idempotencyKey()).isEqualTo("k-1");
  }

  @Test
  void should_leave_envelope_idempotency_key_null_when_event_has_none() {
    assertThat(factory.create(CREATED).message().header().idempotencyKey()).isNull();
  }
}
