package io.codehunters.tracing;

import java.util.Optional;

/** Read access to the trace context of the current thread. */
public interface TraceContextPort {

  /**
   * Current trace identifier.
   *
   * @return the trace id, or empty when no trace is active
   */
  Optional<String> currentTraceId();

  /**
   * Current span identifier.
   *
   * @return the span id, or empty when no trace is active
   */
  Optional<String> currentSpanId();

  /**
   * Current W3C trace context, for propagating to outbound calls or stored events.
   *
   * @return the context, or empty when no trace is active or its ids are not W3C ids
   */
  Optional<TraceParent> currentTraceParent();
}
