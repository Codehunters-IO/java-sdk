package io.codehunters.messaging.factory;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.EventMessage;
import io.codehunters.messaging.EventTypes;
import io.codehunters.messaging.OutboxMessage;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OutboxTraceParentTest {

  private static final String ORIGIN = "ms-test";
  private static final String CURRENT_TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String CURRENT_TRACEPARENT = "00-" + CURRENT_TRACE + "-00f067aa0ba902b7-01";
  private static final TraceIdSource TRACED =
      new TraceIdSource() {
        @Override
        public Optional<String> currentTraceId() {
          return Optional.of(CURRENT_TRACE);
        }

        @Override
        public Optional<String> currentTraceParent() {
          return Optional.of(CURRENT_TRACEPARENT);
        }
      };

  @Test
  void should_supply_no_traceparent_when_source_only_knows_the_trace_id() {
    TraceIdSource traceIdOnly = () -> Optional.of(CURRENT_TRACE);

    assertThat(traceIdOnly.currentTraceParent()).isEmpty();
  }

  @Test
  void should_supply_no_traceparent_when_source_is_none() {
    assertThat(TraceIdSource.none().currentTraceParent()).isEmpty();
  }

  @Test
  void should_stamp_current_traceparent_when_trace_id_is_null() {
    OutboxMessage<EventMessage<String>> outbox =
        new OutboxEventFactory(ORIGIN, TRACED)
            .create("events", null, "agg", "Created", EventTypes.EVENT, "p");

    assertThat(outbox.getTraceparent()).isEqualTo(CURRENT_TRACEPARENT);
  }

  @Test
  void should_stamp_current_traceparent_when_routing_key_message_created() {
    OutboxMessage<EventMessage<String>> outbox =
        new OutboxEventFactory(ORIGIN, TRACED)
            .createRoutingKey("events", "rk", null, "agg", "Created", EventTypes.EVENT, "p");

    assertThat(outbox.getTraceparent()).isEqualTo(CURRENT_TRACEPARENT);
  }

  @Test
  void should_stamp_current_traceparent_when_explicit_trace_id_matches_current_trace() {
    OutboxMessage<EventMessage<String>> outbox =
        new OutboxEventFactory(ORIGIN, TRACED)
            .create("events", CURRENT_TRACE, "agg", "Created", EventTypes.EVENT, "p");

    assertThat(outbox.getTraceparent()).isEqualTo(CURRENT_TRACEPARENT);
  }

  @Test
  void should_leave_traceparent_null_when_explicit_trace_id_differs_from_current_trace() {
    OutboxMessage<EventMessage<String>> outbox =
        new OutboxEventFactory(ORIGIN, TRACED)
            .create("events", "explicit", "agg", "Created", EventTypes.EVENT, "p");

    assertThat(outbox.getTraceparent()).isNull();
  }

  @Test
  void should_leave_traceparent_null_when_no_trace_is_active() {
    OutboxMessage<EventMessage<String>> outbox =
        new OutboxEventFactory(ORIGIN, new EventMessageFactory(ORIGIN))
            .create("events", null, "agg", "Created", EventTypes.EVENT, "p");

    assertThat(outbox.getTraceparent()).isNull();
  }
}
