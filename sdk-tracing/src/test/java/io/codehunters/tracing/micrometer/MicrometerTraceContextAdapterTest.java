package io.codehunters.tracing.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.tracing.TraceParent;
import io.codehunters.tracing.fixture.OtelTracers;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

class MicrometerTraceContextAdapterTest {

  private final Tracer tracer = OtelTracers.sampling();
  private final MicrometerTraceContextAdapter adapter = new MicrometerTraceContextAdapter(tracer);

  @Test
  void should_be_empty_when_no_span_is_in_scope() {
    assertThat(adapter.currentTraceId()).isEmpty();
    assertThat(adapter.currentSpanId()).isEmpty();
    assertThat(adapter.currentTraceParent()).isEmpty();
  }

  @Test
  void should_expose_the_ids_of_the_span_in_scope() {
    Span span = tracer.nextSpan().start();

    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
      assertThat(adapter.currentTraceId()).contains(span.context().traceId());
      assertThat(adapter.currentSpanId()).contains(span.context().spanId());
    } finally {
      span.end();
    }
  }

  @Test
  void should_build_a_sampled_trace_parent_when_the_span_in_scope_is_sampled() {
    Span span = tracer.nextSpan().start();

    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
      assertThat(adapter.currentTraceParent())
          .contains(new TraceParent(span.context().traceId(), span.context().spanId(), true));
    } finally {
      span.end();
    }
  }

  @Test
  void should_build_an_unsampled_trace_parent_when_the_span_in_scope_is_not_sampled() {
    Tracer notSampling = OtelTracers.notSampling();
    MicrometerTraceContextAdapter unsampled = new MicrometerTraceContextAdapter(notSampling);
    Span span = notSampling.nextSpan().start();

    try (Tracer.SpanInScope ignored = notSampling.withSpan(span)) {
      assertThat(unsampled.currentTraceParent())
          .hasValueSatisfying(parent -> assertThat(parent.flags()).isEqualTo("00"));
    } finally {
      span.end();
    }
  }

  @Test
  void should_be_empty_when_the_tracer_is_the_no_op_tracer() {
    MicrometerTraceContextAdapter noop = new MicrometerTraceContextAdapter(Tracer.NOOP);

    assertThat(noop.currentTraceId()).isEmpty();
    assertThat(noop.currentSpanId()).isEmpty();
    assertThat(noop.currentTraceParent()).isEmpty();
  }
}
