package io.codehunters.web.errors.trace;

import java.util.Optional;

/** Supplies the trace id added to problem details as the {@code traceId} extension. */
@FunctionalInterface
public interface TraceIdProvider {

  /**
   * The trace id of the request being answered.
   *
   * @return the trace id, or empty when no trace is active
   */
  Optional<String> currentTraceId();
}
