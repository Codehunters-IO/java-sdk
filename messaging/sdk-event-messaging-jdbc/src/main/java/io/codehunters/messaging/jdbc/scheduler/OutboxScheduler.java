package io.codehunters.messaging.jdbc.scheduler;

import io.codehunters.messaging.OutboxDispatchTrigger;
import io.codehunters.messaging.SendOutboxMessagesPort;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One poll of the outbox dispatch, run every {@code codehunters.outbox.task.fixed-delay} by {@link
 * OutboxSchedulingLifecycle} under the {@code task.lock-name} ShedLock lock, so only one node polls
 * at a time. When the poll's claim comes back full, it asks the {@link OutboxDispatchTrigger} to
 * keep draining the backlog outside the lock instead of waiting for the next poll.
 */
public class OutboxScheduler implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

  private final SendOutboxMessagesPort sendOutboxMessagesPort;
  private final OutboxDispatchTrigger backlogTrigger;

  /**
   * Creates the scheduler without backlog draining: a full claim waits for the next poll.
   *
   * @param sendOutboxMessagesPort the dispatch port
   */
  public OutboxScheduler(SendOutboxMessagesPort sendOutboxMessagesPort) {
    this(sendOutboxMessagesPort, OutboxDispatchTrigger.none());
  }

  /**
   * Creates the scheduler.
   *
   * @param sendOutboxMessagesPort the dispatch port
   * @param backlogTrigger asked for a further dispatch when a poll's claim comes back full
   */
  public OutboxScheduler(
      SendOutboxMessagesPort sendOutboxMessagesPort, OutboxDispatchTrigger backlogTrigger) {
    this.sendOutboxMessagesPort = Objects.requireNonNull(sendOutboxMessagesPort);
    this.backlogTrigger = Objects.requireNonNull(backlogTrigger);
  }

  /** Triggers one dispatch cycle, swallowing exceptions so the scheduler keeps running. */
  @Override
  public void run() {
    try {
      log.debug("[START] - outbox scheduler triggered");
      if (sendOutboxMessagesPort.execute()) {
        backlogTrigger.requestDispatch();
      }
      log.debug("[END] - outbox scheduler completed");
    } catch (Exception e) {
      log.error("[ERROR] - outbox scheduler failed: {}", e.getClass().getName());
    }
  }
}
