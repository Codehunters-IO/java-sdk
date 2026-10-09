package io.codehunters.messaging.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxMessage;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TracedMessagePublisherTest {

  private static final String STORED_TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String STORED_SPAN = "00f067aa0ba902b7";
  private static final String STORED_TRACEPARENT = "00-" + STORED_TRACE + "-" + STORED_SPAN + "-01";

  private SdkTracerProvider provider;
  private Tracer tracer;
  private RecordingPublisher delegate;
  private TracedMessagePublisher publisher;
  private Span scheduler;

  @BeforeEach
  void setUp() {
    provider = SdkTracerProvider.builder().build();
    OtelCurrentTraceContext currentTraceContext = new OtelCurrentTraceContext();
    tracer =
        new OtelTracer(
            provider.get("test"),
            currentTraceContext,
            event -> {},
            new OtelBaggageManager(currentTraceContext, List.of(), List.of()));
    delegate = new RecordingPublisher(tracer);
    publisher = new TracedMessagePublisher(delegate, tracer);
    scheduler = tracer.nextSpan().name("scheduled-dispatch").start();
  }

  @AfterEach
  void tearDown() {
    scheduler.end();
    provider.close();
  }

  @Test
  void should_publish_under_stored_trace_when_dispatching_thread_has_another_trace() {
    try (Tracer.SpanInScope ignored = tracer.withSpan(scheduler)) {
      publisher.publish(message(STORED_TRACEPARENT));
    }

    assertThat(delegate.contexts.get(0).traceId()).isEqualTo(STORED_TRACE);
    assertThat(delegate.contexts.get(0).spanId()).isEqualTo(STORED_SPAN);
  }

  @Test
  void should_restore_dispatching_trace_when_publish_returns() {
    try (Tracer.SpanInScope ignored = tracer.withSpan(scheduler)) {
      publisher.publish(message(STORED_TRACEPARENT));

      assertThat(tracer.currentSpan().context().spanId()).isEqualTo(scheduler.context().spanId());
    }
  }

  @Test
  void should_restore_dispatching_trace_when_publish_fails() {
    delegate.failure = new IllegalStateException("broker down");

    try (Tracer.SpanInScope ignored = tracer.withSpan(scheduler)) {
      assertThatThrownBy(() -> publisher.publish(message(STORED_TRACEPARENT)))
          .isSameAs(delegate.failure);

      assertThat(tracer.currentSpan().context().spanId()).isEqualTo(scheduler.context().spanId());
    }
  }

  @Test
  void should_parent_publish_observation_on_stored_trace_when_dispatch_run_is_observed() {
    ObservationRegistry registry = ObservationRegistry.create();
    registry.observationConfig().observationHandler(new DefaultTracingObservationHandler(tracer));
    delegate.observationRegistry = registry;

    Observation.createNotStarted("scheduled-dispatch", registry)
        .observe(() -> publisher.publish(message(STORED_TRACEPARENT)));

    assertThat(delegate.contexts.get(0).traceId()).isEqualTo(STORED_TRACE);
    assertThat(delegate.contexts.get(0).parentId()).isEqualTo(STORED_SPAN);
  }

  @Test
  void should_publish_in_dispatching_trace_when_message_has_no_traceparent() {
    try (Tracer.SpanInScope ignored = tracer.withSpan(scheduler)) {
      publisher.publish(message(null));
    }

    assertThat(delegate.contexts.get(0).traceId()).isEqualTo(scheduler.context().traceId());
  }

  @Test
  void should_publish_in_dispatching_trace_when_traceparent_is_malformed() {
    try (Tracer.SpanInScope ignored = tracer.withSpan(scheduler)) {
      publisher.publish(message("00-not-a-trace-01"));
    }

    assertThat(delegate.contexts.get(0).traceId()).isEqualTo(scheduler.context().traceId());
  }

  @Test
  void should_publish_in_dispatching_trace_when_trace_id_is_all_zeros() {
    try (Tracer.SpanInScope ignored = tracer.withSpan(scheduler)) {
      publisher.publish(message("00-00000000000000000000000000000000-" + STORED_SPAN + "-01"));
    }

    assertThat(delegate.contexts.get(0).traceId()).isEqualTo(scheduler.context().traceId());
  }

  @Test
  void should_keep_unsampled_flag_when_stored_trace_was_not_sampled() {
    publisher.publish(message("00-" + STORED_TRACE + "-" + STORED_SPAN + "-00"));

    assertThat(delegate.contexts.get(0).sampled()).isFalse();
  }

  @Test
  void should_delegate_unchanged_when_publishing_with_routing_key() {
    publisher.publish("events", "rk", "{}");

    assertThat(delegate.calls).containsExactly("routing:events:rk");
  }

  @Test
  void should_delegate_unchanged_when_publishing_to_topic() {
    publisher.publish("events", "{}");

    assertThat(delegate.calls).containsExactly("topic:events");
  }

  private static OutboxMessage<String> message(String traceparent) {
    return OutboxMessage.<String>builder()
        .topic("events")
        .key("k")
        .message("{}")
        .traceparent(traceparent)
        .build();
  }

  private static final class RecordingPublisher implements MessagePublisherPort {

    private final Tracer tracer;
    private final List<TraceContext> contexts = new ArrayList<>();
    private final List<String> calls = new ArrayList<>();
    private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
    private RuntimeException failure;

    RecordingPublisher(Tracer tracer) {
      this.tracer = tracer;
    }

    @Override
    public void publish(String topic, String routingKey, String payload) {
      calls.add("routing:" + topic + ":" + routingKey);
    }

    @Override
    public void publish(String topic, String payload) {
      calls.add("topic:" + topic);
    }

    @Override
    public void publish(OutboxMessage<String> message) {
      Observation.createNotStarted("send", observationRegistry).observe(this::record);
      if (failure != null) {
        throw failure;
      }
    }

    private void record() {
      contexts.add(tracer.currentSpan().context());
    }
  }
}
