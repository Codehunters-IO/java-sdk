package io.codehunters.tracing;

/** Read access to the current request/message trace context. */
public interface TraceContextPort {

  /**
   * Current trace identifier.
   *
   * @return the current trace ID, or {@code null} if none is set
   */
  String currentTraceId();

  /**
   * Current span identifier.
   *
   * @return the current span ID, or {@code null} if none is set
   */
  String currentSpanId();
}
