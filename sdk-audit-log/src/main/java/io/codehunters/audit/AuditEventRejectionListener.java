package io.codehunters.audit;

/**
 * Notified when the asynchronous publisher cannot queue an {@link AuditEvent}, because the executor
 * is saturated or shutting down. The SDK registers one that increments the {@code
 * audit.events.rejected} counter when Micrometer is present.
 *
 * @since 3.0.0
 */
@FunctionalInterface
public interface AuditEventRejectionListener {

  /**
   * Called once per rejected event, on the caller's thread.
   *
   * @param event the event that was not published
   */
  void rejected(AuditEvent event);
}
