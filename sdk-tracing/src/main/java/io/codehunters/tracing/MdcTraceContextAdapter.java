package io.codehunters.tracing;

import org.slf4j.MDC;

/** {@link TraceContextPort} backed by SLF4J {@link MDC}. */
public class MdcTraceContextAdapter implements TraceContextPort {

  /** MDC key holding the trace ID. */
  public static final String MDC_TRACE_ID = "traceId";

  /** MDC key holding the span ID. */
  public static final String MDC_SPAN_ID = "spanId";

  /** Creates the adapter. */
  public MdcTraceContextAdapter() {}

  @Override
  public String currentTraceId() {
    return MDC.get(MDC_TRACE_ID);
  }

  @Override
  public String currentSpanId() {
    return MDC.get(MDC_SPAN_ID);
  }
}
