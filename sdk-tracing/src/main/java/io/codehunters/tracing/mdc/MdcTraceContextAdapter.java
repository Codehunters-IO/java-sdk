package io.codehunters.tracing.mdc;

import io.codehunters.tracing.TraceContextPort;
import io.codehunters.tracing.TraceHeaders;
import io.codehunters.tracing.TraceParent;
import java.util.Optional;
import org.slf4j.MDC;

/**
 * {@link TraceContextPort} backed by the logging MDC. The fallback used when the application has no
 * Micrometer {@code Tracer}: it reports whatever other code put under the {@code traceId} / {@code
 * spanId} keys, and never starts or propagates a trace itself.
 */
public class MdcTraceContextAdapter implements TraceContextPort {

  /** Creates the adapter. */
  public MdcTraceContextAdapter() {}

  @Override
  public Optional<String> currentTraceId() {
    return Optional.ofNullable(MDC.get(TraceHeaders.MDC_TRACE_ID));
  }

  @Override
  public Optional<String> currentSpanId() {
    return Optional.ofNullable(MDC.get(TraceHeaders.MDC_SPAN_ID));
  }

  /**
   * {@inheritDoc}
   *
   * <p>When the MDC holds only a W3C trace id, the trace is kept with a new span id rather than
   * lost.
   */
  @Override
  public Optional<TraceParent> currentTraceParent() {
    String traceId = MDC.get(TraceHeaders.MDC_TRACE_ID);
    return TraceParent.of(
            traceId, MDC.get(TraceHeaders.MDC_SPAN_ID), MDC.get(TraceHeaders.MDC_TRACE_FLAGS))
        .or(() -> TraceParent.ofTraceId(traceId));
  }
}
