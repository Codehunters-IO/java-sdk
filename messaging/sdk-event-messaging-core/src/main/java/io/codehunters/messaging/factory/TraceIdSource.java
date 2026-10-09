package io.codehunters.messaging.factory;

import java.util.Optional;

/**
 * Supplies the trace of the work in progress: its id, stamped on events whose caller passes no
 * trace id, and its full W3C context, stored with each outbox message so the publish continues it.
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
   * Current W3C trace context as a {@code traceparent} header value ({@code
   * 00-<trace-id>-<span-id>-<flags>}). The default knows none, so a source that supplies only the
   * trace id stores no context with its outbox messages.
   *
   * @return the header value, or empty when no trace is active or it is unknown
   */
  default Optional<String> currentTraceParent() {
    return Optional.empty();
  }

  /**
   * A source that never has a current trace.
   *
   * @return a source always returning empty
   */
  static TraceIdSource none() {
    return Optional::empty;
  }
}
