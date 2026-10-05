package io.codehunters.tracing;

import java.util.Optional;
import org.slf4j.MDC;

/** Reads and writes the trace context in the logging MDC. */
final class TraceMdc {

  private TraceMdc() {}

  static boolean isActive() {
    return MDC.get(TraceHeaders.MDC_TRACE_ID) != null;
  }

  static Optional<TraceParent> current() {
    return TraceParent.of(
        MDC.get(TraceHeaders.MDC_TRACE_ID),
        MDC.get(TraceHeaders.MDC_SPAN_ID),
        MDC.get(TraceHeaders.MDC_TRACE_FLAGS));
  }

  static void put(TraceParent context) {
    MDC.put(TraceHeaders.MDC_TRACE_ID, context.traceId());
    MDC.put(TraceHeaders.MDC_SPAN_ID, context.spanId());
    MDC.put(TraceHeaders.MDC_TRACE_FLAGS, context.flags());
  }

  static void clear() {
    MDC.remove(TraceHeaders.MDC_TRACE_ID);
    MDC.remove(TraceHeaders.MDC_SPAN_ID);
    MDC.remove(TraceHeaders.MDC_TRACE_FLAGS);
  }
}
