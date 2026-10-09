package io.codehunters.messaging.factory;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.EventMessage;
import io.codehunters.messaging.OutboxEvent;
import io.codehunters.messaging.OutboxMessage;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TraceIdSourceTest {

  private static final String ORIGIN = "ms-test";
  private static final String CURRENT_TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final TraceIdSource CURRENT = () -> Optional.of(CURRENT_TRACE);
  private static final OutboxEvent<String> CREATED =
      OutboxEvent.to("events").key("agg").type("Created").payload("p");

  private final EventMessageFactory tracedFactory =
      new EventMessageFactory(ORIGIN, CURRENT, Clock.systemUTC());

  @Test
  void should_supply_no_trace_id_when_source_is_none() {
    assertThat(TraceIdSource.none().currentTraceId()).isEmpty();
  }

  @Test
  void should_use_current_trace_id_when_trace_id_is_null() {
    EventMessage<String> event = tracedFactory.create(CREATED);

    assertThat(event.header().traceId()).isEqualTo(CURRENT_TRACE);
  }

  @Test
  void should_use_current_trace_id_when_trace_id_is_blank() {
    EventMessage<String> event = tracedFactory.create(CREATED.traceId("  "));

    assertThat(event.header().traceId()).isEqualTo(CURRENT_TRACE);
  }

  @Test
  void should_keep_explicit_trace_id_when_current_trace_exists() {
    EventMessage<String> event = tracedFactory.create(CREATED.traceId("explicit"));

    assertThat(event.header().traceId()).isEqualTo("explicit");
  }

  @Test
  void should_keep_null_trace_id_when_no_current_trace() {
    EventMessage<String> event =
        new EventMessageFactory(ORIGIN, TraceIdSource.none(), Clock.systemUTC()).create(CREATED);

    assertThat(event.header().traceId()).isNull();
  }

  @Test
  void should_stamp_current_trace_id_on_outbox_event_when_trace_id_is_null() {
    OutboxMessage<EventMessage<String>> outbox =
        new OutboxEventFactory(tracedFactory).create(CREATED);

    assertThat(outbox.message().header().traceId()).isEqualTo(CURRENT_TRACE);
    assertThat(outbox.message().header().origin()).isEqualTo(ORIGIN);
  }
}
