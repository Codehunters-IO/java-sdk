package io.codehunters.messaging.factory;

import java.util.Optional;

/**
 * Supplies the trace id of the work in progress, stamped on events whose caller passes no trace id.
 */
@FunctionalInterface
public interface TraceIdSource {

  /**
   * Current trace identifier.
   *
   * @return the trace id, or empty when no trace is active
   */
  Optional<String> currentTraceId();

  /**
   * A source that never has a current trace.
   *
   * @return a source always returning empty
   */
  static TraceIdSource none() {
    return Optional::empty;
  }
}
