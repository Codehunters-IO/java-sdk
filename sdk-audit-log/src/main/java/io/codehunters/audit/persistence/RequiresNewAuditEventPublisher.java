package io.codehunters.audit.persistence;

import io.codehunters.audit.AuditEvent;
import io.codehunters.audit.AuditEventPublisherPort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publishes each event in a new transaction of the application's transaction manager, suspending
 * the caller's transaction. The row commits even when the caller's transaction rolls back, or has
 * already been aborted by a SQL error.
 *
 * @since 3.0.0
 */
public class RequiresNewAuditEventPublisher implements AuditEventPublisherPort {

  private final AuditEventPublisherPort delegate;
  private final TransactionTemplate requiresNew;

  /**
   * Creates the publisher.
   *
   * @param delegate the publisher that writes the event
   * @param transactionManager the application's transaction manager
   */
  public RequiresNewAuditEventPublisher(
      AuditEventPublisherPort delegate, PlatformTransactionManager transactionManager) {
    this.delegate = delegate;
    this.requiresNew = new TransactionTemplate(transactionManager);
    this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public void publish(AuditEvent event) {
    requiresNew.executeWithoutResult(status -> delegate.publish(event));
  }
}
