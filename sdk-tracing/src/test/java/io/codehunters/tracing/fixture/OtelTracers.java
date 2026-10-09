package io.codehunters.tracing.fixture;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;

/** Real Micrometer tracers over the OpenTelemetry SDK, without exporters. */
public final class OtelTracers {

  private OtelTracers() {}

  /** A tracer that samples every trace. */
  public static Tracer sampling() {
    return create(Sampler.alwaysOn());
  }

  /** A tracer that samples no trace; its spans still carry valid ids. */
  public static Tracer notSampling() {
    return create(Sampler.alwaysOff());
  }

  private static Tracer create(Sampler sampler) {
    SdkTracerProvider provider = SdkTracerProvider.builder().setSampler(sampler).build();
    return new OtelTracer(provider.get("test"), new OtelCurrentTraceContext(), event -> {});
  }
}
