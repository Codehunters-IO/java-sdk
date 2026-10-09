package io.codehunters.web.errors.trace;

import java.util.Optional;
import org.slf4j.MDC;

/**
 * Reads the trace id from the {@code traceId} MDC key, where both the tracing SDK and Micrometer
 * Tracing put it.
 */
public final class MdcTraceIdProvider implements TraceIdProvider {

  private static final String MDC_KEY = "traceId";

  /** Creates the provider. */
  public MdcTraceIdProvider() {}

  @Override
  public Optional<String> currentTraceId() {
    return Optional.ofNullable(MDC.get(MDC_KEY)).filter(id -> !id.isBlank());
  }
}
