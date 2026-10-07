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

  /**
   * Publishes the event of a call whose work finished on another thread, such as the completion of
   * a {@link java.util.concurrent.CompletionStage} or a Reactor publisher. Any transaction bound to
   * the current thread belongs to someone else, so the event must not join or wait for it.
   * Delegates to {@link #publish(AuditEvent)} by default.
   *
   * @param event the audit event to publish
   * @since 3.0.0
   */
  default void publishDetached(AuditEvent event) {
    publish(event);
  }
}
