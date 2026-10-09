package io.codehunters.tracing;

/** Header and MDC names shared by every component that reads or writes the trace context. */
public final class TraceHeaders {

  /** W3C trace context header. */
  public static final String TRACEPARENT = "traceparent";

  /**
   * Copy of {@link #TRACEPARENT} sent by the gateway and by 0.x producers; read when no valid
   * {@link #TRACEPARENT} is present, written only when {@code
   * codehunters.tracing.legacy-headers.inject} is on.
   */
  public static final String LEGACY_TRACEPARENT = "x-traceparent";

  /** Bare trace id header of 0.x producers; read as a fallback, never written. */
  public static final String TRACE_ID = "x-trace-id";

  /** Bare span id header of 0.x producers; read with {@link #TRACE_ID}, never written. */
  public static final String SPAN_ID = "x-span-id";

  /** MDC key of the trace id; the same key Micrometer Tracing uses. */
  public static final String MDC_TRACE_ID = "traceId";

  /** MDC key of the current span id; the same key Micrometer Tracing uses. */
  public static final String MDC_SPAN_ID = "spanId";

  /** MDC key of the W3C trace flags ({@code 01} sampled, {@code 00} not). */
  public static final String MDC_TRACE_FLAGS = "traceFlags";

  private TraceHeaders() {}
}
