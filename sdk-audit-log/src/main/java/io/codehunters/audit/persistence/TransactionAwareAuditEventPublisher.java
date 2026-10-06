package io.codehunters.audit.persistence;

import io.codehunters.audit.AuditEvent;
import io.codehunters.audit.AuditEventPublisherPort;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Routes audit events according to the caller's Spring transaction, so the audit trail agrees with
 * what was committed.
 *
 * <ul>
 *   <li>Without an active transaction, every event goes to the outside publisher.
 *   <li>An event of a failed call goes to the outside publisher at once, so it survives the
 *       rollback that usually follows.
 *   <li>An event of a successful call, in {@linkplain #atomic atomic} mode, goes to the joined
 *       publisher, which writes inside the transaction: the row commits or rolls back with the
 *       business write.
 *   <li>An event of a successful call, in {@linkplain #deferred deferred} mode, is held until the
 *       transaction completes and then goes to the outside publisher, marked {@code ROLLED_BACK} or
 *       {@code UNKNOWN} unless the transaction committed.
 * </ul>
 *
 * <p>The outside publisher is called from {@code afterCompletion}, where the transaction's
 * resources may still be bound to the thread, so it must write in a transaction of its own or on
 * another thread.
 *
 * @since 3.0.0
 */
public final class TransactionAwareAuditEventPublisher implements AuditEventPublisherPort {

  static final String ROLLED_BACK = "ROLLED_BACK";
  static final String UNKNOWN = "UNKNOWN";

  private final AuditEventPublisherPort joined;
  private final AuditEventPublisherPort outside;

  private TransactionAwareAuditEventPublisher(
      AuditEventPublisherPort joined, AuditEventPublisherPort outside) {
    this.joined = joined;
    this.outside = outside;
  }

  /**
   * Writes success events inside the caller's transaction.
   *
   * @param joined the publisher that writes inside the caller's transaction
   * @param outside the publisher that writes outside it
   * @return the publisher
   */
  public static TransactionAwareAuditEventPublisher atomic(
      AuditEventPublisherPort joined, AuditEventPublisherPort outside) {
    return new TransactionAwareAuditEventPublisher(joined, outside);
  }

  /**
   * Holds success events until the caller's transaction completes.
   *
   * @param outside the publisher that writes outside the caller's transaction
   * @return the publisher
   */
  public static TransactionAwareAuditEventPublisher deferred(AuditEventPublisherPort outside) {
    return new TransactionAwareAuditEventPublisher(null, outside);
  }

  @Override
  public void publish(AuditEvent event) {
    if (!inTransaction() || !Boolean.TRUE.equals(event.success())) {
      outside.publish(event);
    } else if (joined != null) {
      joined.publish(event);
    } else {
      TransactionSynchronizationManager.registerSynchronization(new AfterCompletion(event));
    }
  }

  private static boolean inTransaction() {
    return TransactionSynchronizationManager.isActualTransactionActive()
        && TransactionSynchronizationManager.isSynchronizationActive();
  }

  private final class AfterCompletion implements TransactionSynchronization {

    private final AuditEvent event;

    private AfterCompletion(AuditEvent event) {
      this.event = event;
    }

    @Override
    public void afterCompletion(int status) {
      outside.publish(outcome(status));
    }

    private AuditEvent outcome(int status) {
      return switch (status) {
        case STATUS_COMMITTED -> event;
        case STATUS_ROLLED_BACK -> event.withOutcome(ROLLED_BACK, false, ROLLED_BACK);
        default -> event.withOutcome(UNKNOWN, null, UNKNOWN);
      };
    }
  }
}
