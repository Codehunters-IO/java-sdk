package io.codehunters.tracing.mdc;

import io.codehunters.tracing.TraceContextPort;
import io.codehunters.tracing.TraceHeaders;
import io.codehunters.tracing.TraceParent;
import io.codehunters.tracing.internal.TraceMdc;
import java.util.Optional;
import org.slf4j.MDC;

/** {@link TraceContextPort} backed by the logging MDC, as populated by this SDK or Micrometer. */
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

  @Override
  public Optional<TraceParent> currentTraceParent() {
    return TraceMdc.current();
  }
}
