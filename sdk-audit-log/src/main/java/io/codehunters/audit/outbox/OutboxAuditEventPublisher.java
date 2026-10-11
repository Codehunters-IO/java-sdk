package io.codehunters.audit.outbox;

import io.codehunters.audit.AuditEvent;
import io.codehunters.audit.AuditEventPublisherPort;
import io.codehunters.messaging.OutboxWriter;
import io.codehunters.messaging.factory.OutboxEventFactory;

/**
 * Writes audit events through the transactional outbox.
 *
 * @since 5.1.0
 */
public class OutboxAuditEventPublisher implements AuditEventPublisherPort {

  /**
   * Creates the publisher.
   *
   * @param writer the outbox writer
   * @param factory the outbox event factory
   * @param settings the topic and detached transaction manager
   */
  public OutboxAuditEventPublisher(
      OutboxWriter writer, OutboxEventFactory factory, OutboxAuditSettings settings) {}

  @Override
  public void publish(AuditEvent event) {
    throw new UnsupportedOperationException("not implemented");
  }
}
