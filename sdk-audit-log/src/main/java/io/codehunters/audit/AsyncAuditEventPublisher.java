package io.codehunters.audit;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes audit events on an {@link Executor} so the audited call does not wait for the sink.
 * Never throws to the caller: a rejected event is logged and reported to the {@link
 * AuditEventRejectionListener}, and a failure inside the delegate is logged on the worker thread.
 *
 * @since 3.0.0
 */
public class AsyncAuditEventPublisher implements AuditEventPublisherPort {

  private static final Logger log = LoggerFactory.getLogger(AsyncAuditEventPublisher.class);

  private final AuditEventPublisherPort delegate;
  private final Executor executor;
  private final AuditEventRejectionListener rejectionListener;

  /**
   * Creates the publisher.
   *
   * @param delegate the publisher that writes the event
   * @param executor the executor that runs the delegate
   * @param rejectionListener notified when the executor refuses an event
   */
  public AsyncAuditEventPublisher(
      AuditEventPublisherPort delegate,
      Executor executor,
      AuditEventRejectionListener rejectionListener) {
    this.delegate = delegate;
    this.executor = executor;
    this.rejectionListener = rejectionListener;
  }

  @Override
  public void publish(AuditEvent event) {
    try {
      executor.execute(() -> deliver(event));
    } catch (RejectedExecutionException ex) {
      log.warn(
          "Audit event dropped, executor rejected it: entityType={} action={}",
          event.entityType(),
          event.action());
      rejectionListener.rejected(event);
    }
  }

  private void deliver(AuditEvent event) {
    try {
      delegate.publish(event);
    } catch (RuntimeException ex) {
      log.error(
          "Failed to publish audit event: entityType={} action={}",
          event.entityType(),
          event.action(),
          ex);
    }
  }
}
