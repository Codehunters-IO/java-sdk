package io.codehunters.audit;

/**
 * Notified when an {@link AuditEvent} could not be written and the failure was not propagated to
 * the audited call. The SDK registers one that increments the {@code audit.events.failed} counter
 * when Micrometer is present.
 *
 * @since 3.0.0
 */
@FunctionalInterface
public interface AuditEventFailureListener {

  /**
   * Called once per event that was not written.
   *
   * @param event the event that was lost
   */
  void failed(AuditEvent event);
}
