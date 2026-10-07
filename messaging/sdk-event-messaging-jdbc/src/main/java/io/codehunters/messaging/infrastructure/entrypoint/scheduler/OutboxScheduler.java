package io.codehunters.messaging.infrastructure.entrypoint.scheduler;

import io.codehunters.messaging.application.port.SendOutboxMessagesPort;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Inbound scheduler that periodically triggers outbox dispatch. Coordinated across instances by
 * ShedLock so only one node runs at a time.
 */
@Slf4j
public class OutboxScheduler {

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
  @Scheduled(fixedDelayString = "${codehunters.outbox.task.fixed-delay:5000}")
  @SchedulerLock(
      name = "${codehunters.outbox.task.lock-name:outbox-${spring.application.name:unknown}}",
      lockAtMostFor = "${codehunters.outbox.task.lock-at-most:300000}",
      lockAtLeastFor = "${codehunters.outbox.task.lock-at-least:10000}")
  public void processOutboxMessages() {
    try {
      log.debug("[START] - outbox scheduler triggered");
      sendOutboxMessagesPort.execute();
      log.debug("[END] - outbox scheduler completed");
    } catch (Exception e) {
      log.error("[ERROR] - outbox scheduler failed: {}", e.getClass().getName());
    }
  }
}
