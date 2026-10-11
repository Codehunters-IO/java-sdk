package io.codehunters.audit.outbox;

import org.springframework.transaction.PlatformTransactionManager;

/**
 * Settings of the {@link OutboxAuditEventPublisher}.
 *
 * @param topic the outbox topic every audit event is written to; never blank
 * @param detachedTransactionManager the transaction manager used to suspend the current transaction
 *     for {@link OutboxAuditEventPublisher#publishDetached}, or {@code null} to write detached
 *     events directly
 * @since 5.1.0
 */
public record OutboxAuditSettings(
    String topic, PlatformTransactionManager detachedTransactionManager) {

  /**
   * Validates the topic.
   *
   * @throws IllegalArgumentException when the topic is null or blank
   */
  public OutboxAuditSettings {
    throw new UnsupportedOperationException("not implemented");
  }
}
