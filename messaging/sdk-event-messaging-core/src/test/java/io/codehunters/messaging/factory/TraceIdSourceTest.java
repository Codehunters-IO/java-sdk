package io.codehunters.messaging.factory;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.EventMessage;
import io.codehunters.messaging.EventTypes;
import io.codehunters.messaging.OutboxMessage;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TraceIdSourceTest {

  private static final String ORIGIN = "ms-test";
  private static final String CURRENT_TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final TraceIdSource CURRENT = () -> Optional.of(CURRENT_TRACE);

  private final EventMessageFactory tracedFactory = new EventMessageFactory(ORIGIN, CURRENT);

  @Test
  void should_supply_no_trace_id_when_source_is_none() {
    assertThat(TraceIdSource.none().currentTraceId()).isEmpty();
  }

  @Test
  void should_use_current_trace_id_when_trace_id_is_null() {
    EventMessage<String> event =
        tracedFactory.create(null, "agg", "Created", EventTypes.EVENT, "p");

    assertThat(event.getHeader().getTraceId()).isEqualTo(CURRENT_TRACE);
  }

  @Test
  void should_use_current_trace_id_when_trace_id_is_blank() {
    EventMessage<String> event =
        tracedFactory.create("  ", "agg", "Created", EventTypes.EVENT, "p");

    assertThat(event.getHeader().getTraceId()).isEqualTo(CURRENT_TRACE);
  }

  @Test
  void should_keep_explicit_trace_id_when_current_trace_exists() {
    EventMessage<String> event =
        tracedFactory.create("explicit", "agg", "Created", EventTypes.EVENT, "p");

    assertThat(event.getHeader().getTraceId()).isEqualTo("explicit");
  }

  @Test
  void should_keep_null_trace_id_when_no_current_trace() {
    EventMessage<String> event =
        new EventMessageFactory(ORIGIN).create(null, "agg", "Created", EventTypes.EVENT, "p");

    assertThat(event.getHeader().getTraceId()).isNull();
  }

  @Test
  void should_stamp_current_trace_id_on_outbox_event_when_trace_id_is_null() {
    OutboxMessage<EventMessage<String>> outbox =
        new OutboxEventFactory(ORIGIN, CURRENT)
            .create("events", null, "agg", "Created", EventTypes.EVENT, "p");

    assertThat(outbox.getMessage().getHeader().getTraceId()).isEqualTo(CURRENT_TRACE);
    assertThat(outbox.getMessage().getHeader().getOrigin()).isEqualTo(ORIGIN);
  }
}
