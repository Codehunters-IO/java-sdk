package io.codehunters.tracing.internal;

import io.codehunters.tracing.TraceHeaders;
import io.codehunters.tracing.TraceParent;
import java.util.Optional;
import org.slf4j.MDC;

/** Reads and writes the trace context in the logging MDC. */
public final class TraceMdc {

  private TraceMdc() {}

  public static boolean isActive() {
    return MDC.get(TraceHeaders.MDC_TRACE_ID) != null;
  }

  /**
   * The context in the MDC. When only a W3C trace id is there (set by code other than this SDK),
   * the trace is kept with a new span rather than lost.
   */
  public static Optional<TraceParent> current() {
    String traceId = MDC.get(TraceHeaders.MDC_TRACE_ID);
    return TraceParent.of(
            traceId, MDC.get(TraceHeaders.MDC_SPAN_ID), MDC.get(TraceHeaders.MDC_TRACE_FLAGS))
        .or(() -> TraceParent.ofTraceId(traceId));
  }

  public static void put(TraceParent context) {
    MDC.put(TraceHeaders.MDC_TRACE_ID, context.traceId());
    MDC.put(TraceHeaders.MDC_SPAN_ID, context.spanId());
    MDC.put(TraceHeaders.MDC_TRACE_FLAGS, context.flags());
  }

  public static void clear() {
    MDC.remove(TraceHeaders.MDC_TRACE_ID);
    MDC.remove(TraceHeaders.MDC_SPAN_ID);
    MDC.remove(TraceHeaders.MDC_TRACE_FLAGS);
  }
}
