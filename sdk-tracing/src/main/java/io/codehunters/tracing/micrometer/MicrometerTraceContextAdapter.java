package io.codehunters.tracing.micrometer;

import io.codehunters.tracing.TraceContextPort;
import io.codehunters.tracing.TraceParent;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.Objects;
import java.util.Optional;

/**
 * {@link TraceContextPort} backed by the Micrometer {@link Tracer}: the ids are those of the span
 * in scope, the same ones the tracer exports and propagates, whichever bridge (OpenTelemetry or
 * Brave) the application uses.
 */
public class MicrometerTraceContextAdapter implements TraceContextPort {

  private final Tracer tracer;

  /**
   * Creates the adapter.
   *
   * @param tracer the application's tracer
   */
  public MicrometerTraceContextAdapter(Tracer tracer) {
    this.tracer = Objects.requireNonNull(tracer, "tracer");
  }

  @Override
  public Optional<String> currentTraceId() {
    return current().map(TraceContext::traceId).filter(id -> !id.isBlank());
  }

  @Override
  public Optional<String> currentSpanId() {
    return current().map(TraceContext::spanId).filter(id -> !id.isBlank());
  }

  /**
   * {@inheritDoc}
   *
   * <p>The sampled flag is the tracer's decision; a deferred decision is reported as not sampled.
   */
  @Override
  public Optional<TraceParent> currentTraceParent() {
    return current().flatMap(MicrometerTraceContextAdapter::toTraceParent);
  }

  private Optional<TraceContext> current() {
    return Optional.ofNullable(tracer.currentTraceContext().context());
  }

  private static Optional<TraceParent> toTraceParent(TraceContext context) {
    String flags = Boolean.TRUE.equals(context.sampled()) ? "01" : "00";
    return TraceParent.of(context.traceId(), context.spanId(), flags);
  }
}
