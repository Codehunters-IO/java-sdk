package io.codehunters.messaging.factory;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.EventMessage;
import io.codehunters.messaging.OutboxEvent;
import io.codehunters.messaging.OutboxMessage;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OutboxTraceParentTest {

  private static final String ORIGIN = "ms-test";
  private static final String CURRENT_TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String CURRENT_SPAN = "00f067aa0ba902b7";
  private static final String CURRENT_TRACEPARENT =
      "00-" + CURRENT_TRACE + "-" + CURRENT_SPAN + "-01";
  private static final OutboxEvent<String> CREATED =
      OutboxEvent.to("events").key("agg").type("Created").payload("p");

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
    OutboxMessage<EventMessage<String>> outbox = factoryWith(CURRENT_TRACEPARENT).create(CREATED);

    assertThat(outbox.traceparent()).isEqualTo(CURRENT_TRACEPARENT);
  }

  @Test
  void should_stamp_current_traceparent_when_event_has_routing_key() {
    OutboxMessage<EventMessage<String>> outbox =
        factoryWith(CURRENT_TRACEPARENT).create(CREATED.routingKey("rk"));

    assertThat(outbox.traceparent()).isEqualTo(CURRENT_TRACEPARENT);
  }

  @Test
  void should_stamp_current_traceparent_when_explicit_trace_id_matches_current_trace() {
    OutboxMessage<EventMessage<String>> outbox =
        factoryWith(CURRENT_TRACEPARENT).create(CREATED.traceId(CURRENT_TRACE));

    assertThat(outbox.traceparent()).isEqualTo(CURRENT_TRACEPARENT);
  }

  @Test
  void should_leave_traceparent_null_when_explicit_trace_id_differs_from_current_trace() {
    OutboxMessage<EventMessage<String>> outbox =
        factoryWith(CURRENT_TRACEPARENT).create(CREATED.traceId("explicit"));

    assertThat(outbox.traceparent()).isNull();
  }

  @Test
  void should_leave_traceparent_null_when_explicit_trace_id_equals_current_span_id() {
    OutboxMessage<EventMessage<String>> outbox =
        factoryWith(CURRENT_TRACEPARENT).create(CREATED.traceId(CURRENT_SPAN));

    assertThat(outbox.traceparent()).isNull();
  }

  @Test
  void should_leave_traceparent_null_when_current_traceparent_is_longer_than_55_characters() {
    OutboxMessage<EventMessage<String>> outbox =
        factoryWith(CURRENT_TRACEPARENT + "-extra").create(CREATED);

    assertThat(outbox.traceparent()).isNull();
  }

  @Test
  void should_leave_traceparent_null_when_current_traceparent_is_malformed() {
    OutboxMessage<EventMessage<String>> outbox =
        factoryWith("00-" + CURRENT_TRACE + "-not-a-span-01").create(CREATED);

    assertThat(outbox.traceparent()).isNull();
  }

  @Test
  void should_leave_traceparent_null_when_current_traceparent_has_version_ff() {
    OutboxMessage<EventMessage<String>> outbox =
        factoryWith("ff" + CURRENT_TRACEPARENT.substring(2)).create(CREATED);

    assertThat(outbox.traceparent()).isNull();
  }

  @Test
  void should_leave_traceparent_null_when_no_trace_is_active() {
    OutboxMessage<EventMessage<String>> outbox =
        new OutboxEventFactory(
                new EventMessageFactory(ORIGIN, TraceIdSource.none(), Clock.systemUTC()))
            .create(CREATED);

    assertThat(outbox.traceparent()).isNull();
  }

  private static OutboxEventFactory factoryWith(String traceparent) {
    return new OutboxEventFactory(
        new EventMessageFactory(ORIGIN, sourceWith(traceparent), Clock.systemUTC()));
  }

  private static TraceIdSource sourceWith(String traceparent) {
    return new TraceIdSource() {
      @Override
      public Optional<String> currentTraceId() {
        return Optional.of(CURRENT_TRACE);
      }

      @Override
      public Optional<String> currentTraceParent() {
        return Optional.of(traceparent);
      }
    };
  }
}
