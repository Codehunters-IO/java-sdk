package io.codehunters.audit;

/**
 * Port for publishing audit events. Consumers implement this to persist events to a database,
 * message broker, or any other sink.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface AuditEventPublisherPort {

  /**
   * Publishes an audit event to the configured sink.
   *
   * @param event the audit event to publish
   */
  void publish(AuditEvent event);
}
