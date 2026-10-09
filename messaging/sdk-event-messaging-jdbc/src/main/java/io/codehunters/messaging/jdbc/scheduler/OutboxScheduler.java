package io.codehunters.messaging.jdbc.scheduler;

import io.codehunters.messaging.SendOutboxMessagesPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One poll of the outbox dispatch, run every {@code codehunters.outbox.task.fixed-delay} by {@link
 * OutboxSchedulingLifecycle} under the {@code task.lock-name} ShedLock lock, so only one node polls
 * at a time.
 */
public class OutboxScheduler implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

  private final SendOutboxMessagesPort sendOutboxMessagesPort;

  /**
   * Creates the scheduler.
   *
   * @param sendOutboxMessagesPort the dispatch port
   */
  public OutboxScheduler(SendOutboxMessagesPort sendOutboxMessagesPort) {
    this.sendOutboxMessagesPort = sendOutboxMessagesPort;
  }

  /** Triggers one dispatch cycle, swallowing exceptions so the scheduler keeps running. */
  @Override
  public void run() {
    try {
      log.debug("[START] - outbox scheduler triggered");
      sendOutboxMessagesPort.execute();
      log.debug("[END] - outbox scheduler completed");
    } catch (Exception e) {
      log.error("[ERROR] - outbox scheduler failed: {}", e.getClass().getName());
    }
  }
}
