package io.codehunters.tracing.otel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.codehunters.tracing.TraceHeaders;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegacyTraceHeadersPropagatorTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String SPAN_ID = "00f067aa0ba902b7";
  private static final String OTHER_TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
  private static final String OTHER_SPAN_ID = "b7ad6b7169203331";
  private static final String SAMPLED_PARENT = "00-" + TRACE_ID + "-" + SPAN_ID + "-01";
  private static final String UNSAMPLED_PARENT = "00-" + TRACE_ID + "-" + SPAN_ID + "-00";
  private static final String OTHER_PARENT = "00-" + OTHER_TRACE_ID + "-" + OTHER_SPAN_ID + "-01";
  private static final String LOW_TRACE_ID = "4bf92f3577b34da60000000000000001";
  private static final String HIGH_TRACE_ID = "4bf92f3577b34da67ffffffffffffff0";
  private static final double ALWAYS = 1.0;
  private static final double NEVER = 0.0;
  private static final double HALF = 0.5;

  private static final TextMapGetter<Map<String, String>> GETTER =
      new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
          return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
          return carrier.get(key);
        }
      };

  private static final TextMapSetter<Map<String, String>> SETTER = Map::put;

  private final LegacyTraceHeadersPropagator propagator =
      new LegacyTraceHeadersPropagator(false, ALWAYS);

  @Test
  void should_extract_the_remote_parent_when_only_x_traceparent_is_present() {
    SpanContext parent = extract(Map.of(TraceHeaders.LEGACY_TRACEPARENT, SAMPLED_PARENT));

    assertThat(parent.getTraceId()).isEqualTo(TRACE_ID);
    assertThat(parent.getSpanId()).isEqualTo(SPAN_ID);
    assertThat(parent.isSampled()).isTrue();
    assertThat(parent.isRemote()).isTrue();
  }

  @Test
  void should_keep_the_unsampled_flag_when_x_traceparent_is_not_sampled() {
    SpanContext parent = extract(Map.of(TraceHeaders.LEGACY_TRACEPARENT, UNSAMPLED_PARENT));

    assertThat(parent.isSampled()).isFalse();
  }

  @Test
  void should_extract_x_trace_id_and_x_span_id_when_no_traceparent_header_is_present() {
    SpanContext parent =
        extract(Map.of(TraceHeaders.TRACE_ID, TRACE_ID, TraceHeaders.SPAN_ID, SPAN_ID));

    assertThat(parent.getTraceId()).isEqualTo(TRACE_ID);
    assertThat(parent.getSpanId()).isEqualTo(SPAN_ID);
    assertThat(parent.isRemote()).isTrue();
  }

  @Test
  void should_extract_x_trace_id_with_a_new_parent_span_when_x_span_id_is_absent() {
    SpanContext parent = extract(Map.of(TraceHeaders.TRACE_ID, TRACE_ID));

    assertThat(parent.getTraceId()).isEqualTo(TRACE_ID);
    assertThat(parent.isValid()).isTrue();
  }

  @Test
  void should_sample_x_trace_id_and_x_span_id_when_the_probability_is_one() {
    SpanContext parent =
        extractWith(
            new LegacyTraceHeadersPropagator(false, ALWAYS),
            Map.of(TraceHeaders.TRACE_ID, TRACE_ID, TraceHeaders.SPAN_ID, SPAN_ID));

    assertThat(parent.isSampled()).isTrue();
  }

  @Test
  void should_not_sample_x_trace_id_and_x_span_id_when_the_probability_is_zero() {
    SpanContext parent =
        extractWith(
            new LegacyTraceHeadersPropagator(false, NEVER),
            Map.of(TraceHeaders.TRACE_ID, TRACE_ID, TraceHeaders.SPAN_ID, SPAN_ID));

    assertThat(parent.isSampled()).isFalse();
  }

  @Test
  void should_not_sample_x_trace_id_alone_when_the_probability_is_zero() {
    SpanContext parent =
        extractWith(
            new LegacyTraceHeadersPropagator(false, NEVER),
            Map.of(TraceHeaders.TRACE_ID, TRACE_ID));

    assertThat(parent.isValid()).isTrue();
    assertThat(parent.isSampled()).isFalse();
  }

  @Test
  void should_sample_x_trace_id_when_its_random_part_falls_within_the_probability() {
    SpanContext parent =
        extractWith(
            new LegacyTraceHeadersPropagator(false, HALF),
            Map.of(TraceHeaders.TRACE_ID, LOW_TRACE_ID));

    assertThat(parent.isSampled()).isTrue();
  }

  @Test
  void should_not_sample_x_trace_id_when_its_random_part_falls_outside_the_probability() {
    SpanContext parent =
        extractWith(
            new LegacyTraceHeadersPropagator(false, HALF),
            Map.of(TraceHeaders.TRACE_ID, HIGH_TRACE_ID, TraceHeaders.SPAN_ID, SPAN_ID));

    assertThat(parent.isSampled()).isFalse();
  }

  @Test
  void should_keep_the_sampled_flag_of_x_traceparent_when_the_probability_is_zero() {
    SpanContext parent =
        extractWith(
            new LegacyTraceHeadersPropagator(false, NEVER),
            Map.of(TraceHeaders.LEGACY_TRACEPARENT, SAMPLED_PARENT));

    assertThat(parent.isSampled()).isTrue();
  }

  @Test
  void should_keep_the_unsampled_flag_of_x_traceparent_when_the_probability_is_one() {
    SpanContext parent =
        extractWith(
            new LegacyTraceHeadersPropagator(false, ALWAYS),
            Map.of(TraceHeaders.LEGACY_TRACEPARENT, UNSAMPLED_PARENT));

    assertThat(parent.isSampled()).isFalse();
  }

  @Test
  void should_reject_a_probability_outside_zero_and_one() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new LegacyTraceHeadersPropagator(false, 1.5));
  }

  @Test
  void should_prefer_x_traceparent_when_x_trace_id_is_also_present() {
    SpanContext parent =
        extract(
            Map.of(
                TraceHeaders.LEGACY_TRACEPARENT, SAMPLED_PARENT,
                TraceHeaders.TRACE_ID, OTHER_TRACE_ID));

    assertThat(parent.getTraceId()).isEqualTo(TRACE_ID);
  }

  @Test
  void should_leave_the_context_unchanged_when_a_valid_traceparent_is_present() {
    Map<String, String> carrier =
        Map.of(
            TraceHeaders.TRACEPARENT,
            OTHER_PARENT,
            TraceHeaders.LEGACY_TRACEPARENT,
            SAMPLED_PARENT);

    assertThat(propagator.extract(Context.root(), carrier, GETTER)).isSameAs(Context.root());
  }

  @Test
  void should_read_the_legacy_headers_when_traceparent_is_invalid() {
    SpanContext parent =
        extract(
            Map.of(
                TraceHeaders.TRACEPARENT,
                "garbage",
                TraceHeaders.LEGACY_TRACEPARENT,
                SAMPLED_PARENT));

    assertThat(parent.getTraceId()).isEqualTo(TRACE_ID);
  }

  @Test
  void should_leave_the_context_unchanged_when_a_previous_propagator_extracted_a_remote_parent() {
    Context extracted = Context.root().with(Span.wrap(remoteParent()));

    assertThat(
            propagator.extract(
                extracted, Map.of(TraceHeaders.LEGACY_TRACEPARENT, SAMPLED_PARENT), GETTER))
        .isSameAs(extracted);
  }

  @Test
  void should_leave_the_context_unchanged_when_the_legacy_ids_are_not_w3c_ids() {
    Map<String, String> carrier =
        Map.of(TraceHeaders.TRACE_ID, "7f1c2a4e-0b7d-4f7e-9d0a-1c2b3d4e5f60");

    assertThat(propagator.extract(Context.root(), carrier, GETTER)).isSameAs(Context.root());
  }

  @Test
  void should_leave_the_context_unchanged_when_there_is_no_carrier() {
    assertThat(propagator.extract(Context.root(), null, GETTER)).isSameAs(Context.root());
  }

  @Test
  void should_extract_from_the_root_context_when_none_is_given() {
    SpanContext parent =
        Span.fromContext(
                propagator.extract(
                    null, Map.of(TraceHeaders.LEGACY_TRACEPARENT, SAMPLED_PARENT), GETTER))
            .getSpanContext();

    assertThat(parent.getTraceId()).isEqualTo(TRACE_ID);
  }

  @Test
  void should_let_traceparent_win_when_composed_after_w3c() {
    TextMapPropagator composite =
        TextMapPropagator.composite(W3CTraceContextPropagator.getInstance(), propagator);

    assertThat(extractWith(composite, bothHeaders()).getTraceId()).isEqualTo(OTHER_TRACE_ID);
  }

  @Test
  void should_let_traceparent_win_when_composed_before_w3c() {
    TextMapPropagator composite =
        TextMapPropagator.composite(propagator, W3CTraceContextPropagator.getInstance());

    assertThat(extractWith(composite, bothHeaders()).getTraceId()).isEqualTo(OTHER_TRACE_ID);
  }

  @Test
  void should_not_inject_when_injection_is_disabled() {
    Map<String, String> carrier = new HashMap<>();

    propagator.inject(Context.root().with(Span.wrap(remoteParent())), carrier, SETTER);

    assertThat(carrier).isEmpty();
  }

  @Test
  void should_inject_x_traceparent_when_injection_is_enabled() {
    Map<String, String> carrier = new HashMap<>();

    new LegacyTraceHeadersPropagator(true, ALWAYS)
        .inject(Context.root().with(Span.wrap(remoteParent())), carrier, SETTER);

    assertThat(carrier).containsExactly(Map.entry(TraceHeaders.LEGACY_TRACEPARENT, SAMPLED_PARENT));
  }

  @Test
  void should_not_inject_when_the_context_has_no_valid_span() {
    Map<String, String> carrier = new HashMap<>();

    new LegacyTraceHeadersPropagator(true, ALWAYS).inject(Context.root(), carrier, SETTER);

    assertThat(carrier).isEmpty();
  }

  @Test
  void should_list_the_legacy_headers_as_its_fields() {
    assertThat(propagator.fields())
        .containsExactly(
            TraceHeaders.LEGACY_TRACEPARENT, TraceHeaders.TRACE_ID, TraceHeaders.SPAN_ID);
  }

  private SpanContext extract(Map<String, String> carrier) {
    return extractWith(propagator, carrier);
  }

  private static SpanContext extractWith(TextMapPropagator subject, Map<String, String> carrier) {
    return Span.fromContext(subject.extract(Context.root(), carrier, GETTER)).getSpanContext();
  }

  private static Map<String, String> bothHeaders() {
    return Map.of(
        TraceHeaders.TRACEPARENT, OTHER_PARENT, TraceHeaders.LEGACY_TRACEPARENT, SAMPLED_PARENT);
  }

  private static SpanContext remoteParent() {
    return SpanContext.createFromRemoteParent(
        TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
  }
}
