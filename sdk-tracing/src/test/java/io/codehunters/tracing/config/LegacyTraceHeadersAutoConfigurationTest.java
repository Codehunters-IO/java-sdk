package io.codehunters.tracing.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.tracing.TraceContextPort;
import io.codehunters.tracing.TraceHeaders;
import io.codehunters.tracing.micrometer.MicrometerTraceContextAdapter;
import io.codehunters.tracing.otel.LegacyTraceHeadersPropagator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LegacyTraceHeadersAutoConfigurationTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String SPAN_ID = "00f067aa0ba902b7";
  private static final String LEGACY_PARENT = "00-" + TRACE_ID + "-" + SPAN_ID + "-01";
  private static final String W3C_TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
  private static final String W3C_PARENT = "00-" + W3C_TRACE_ID + "-b7ad6b7169203331-01";

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

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(LegacyTraceHeadersAutoConfiguration.class));

  private final ApplicationContextRunner withOpenTelemetry =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OpenTelemetrySdkAutoConfiguration.class,
                  MicrometerTracingAutoConfiguration.class,
                  OpenTelemetryTracingAutoConfiguration.class,
                  TracingAutoConfiguration.class,
                  LegacyTraceHeadersAutoConfiguration.class));

  @Test
  void should_register_an_extract_only_propagator_by_default() {
    runner.run(
        context -> {
          Map<String, String> carrier = new HashMap<>();
          context
              .getBean(LegacyTraceHeadersPropagator.class)
              .inject(sampledSpan(), carrier, SETTER);

          assertThat(carrier).isEmpty();
        });
  }

  @Test
  void should_inject_x_traceparent_when_configured() {
    runner
        .withPropertyValues("codehunters.tracing.legacy-headers.inject=true")
        .run(
            context -> {
              Map<String, String> carrier = new HashMap<>();
              context
                  .getBean(LegacyTraceHeadersPropagator.class)
                  .inject(sampledSpan(), carrier, SETTER);

              assertThat(carrier).containsEntry(TraceHeaders.LEGACY_TRACEPARENT, LEGACY_PARENT);
            });
  }

  @Test
  void should_register_nothing_when_legacy_headers_are_disabled() {
    runner
        .withPropertyValues("codehunters.tracing.legacy-headers.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(LegacyTraceHeadersPropagator.class));
  }

  @Test
  void should_register_nothing_when_tracing_is_disabled() {
    runner
        .withPropertyValues("codehunters.tracing.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(LegacyTraceHeadersPropagator.class));
  }

  @Test
  void should_register_nothing_when_opentelemetry_is_absent() {
    runner
        .withClassLoader(new FilteredClassLoader(TextMapPropagator.class))
        .run(context -> assertThat(context).doesNotHaveBean(LegacyTraceHeadersPropagator.class));
  }

  @Test
  void should_extract_x_traceparent_through_boot_propagators_when_no_traceparent_is_sent() {
    withOpenTelemetry.run(
        context ->
            assertThat(extract(context, Map.of(TraceHeaders.LEGACY_TRACEPARENT, LEGACY_PARENT)))
                .isEqualTo(TRACE_ID));
  }

  @Test
  void should_extract_traceparent_through_boot_propagators_when_both_headers_are_sent() {
    withOpenTelemetry.run(
        context ->
            assertThat(
                    extract(
                        context,
                        Map.of(
                            TraceHeaders.TRACEPARENT, W3C_PARENT,
                            TraceHeaders.LEGACY_TRACEPARENT, LEGACY_PARENT)))
                .isEqualTo(W3C_TRACE_ID));
  }

  @Test
  void should_back_the_port_with_boot_tracer_when_opentelemetry_is_configured() {
    withOpenTelemetry.run(
        context ->
            assertThat(context.getBean(TraceContextPort.class))
                .isInstanceOf(MicrometerTraceContextAdapter.class));
  }

  private static String extract(AssertableApplicationContext context, Map<String, String> carrier) {
    TextMapPropagator propagator = context.getBean(ContextPropagators.class).getTextMapPropagator();
    return Span.fromContext(propagator.extract(Context.root(), carrier, GETTER))
        .getSpanContext()
        .getTraceId();
  }

  private static Context sampledSpan() {
    return Context.root()
        .with(
            Span.wrap(
                SpanContext.createFromRemoteParent(
                    TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault())));
  }
}
