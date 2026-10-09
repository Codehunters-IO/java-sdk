package io.codehunters.audit.persistence;

import io.codehunters.audit.AuditEvent;
import io.codehunters.audit.AuditEventFailureListener;
import io.codehunters.audit.AuditEventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes through a delegate and never throws: a failure is logged with the event's entity type
 * and action and the exception class, and reported to the {@link AuditEventFailureListener}. Used
 * when {@code fail-on-error} is off, so a lost event is counted rather than silent.
 *
 * @since 3.0.0
 */
public class FailSafeAuditEventPublisher implements AuditEventPublisherPort {

  private static final Logger log = LoggerFactory.getLogger(FailSafeAuditEventPublisher.class);

  private final AuditEventPublisherPort delegate;
  private final AuditEventFailureListener failureListener;

  /**
   * Creates the publisher.
   *
   * @param delegate the publisher that writes the event
   * @param failureListener notified when the delegate fails
   */
  public FailSafeAuditEventPublisher(
      AuditEventPublisherPort delegate, AuditEventFailureListener failureListener) {
    this.delegate = delegate;
    this.failureListener = failureListener;
  }

  @Override
  public void publish(AuditEvent event) {
    try {
      delegate.publish(event);
    } catch (RuntimeException ex) {
      log.error(
          "Audit event lost: entityType={} action={} exception={}",
          event.entityType(),
          event.action(),
          ex.getClass().getName());
      failureListener.failed(event);
    }
  }
}
