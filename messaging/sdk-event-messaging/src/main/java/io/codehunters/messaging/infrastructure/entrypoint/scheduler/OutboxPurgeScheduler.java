package io.codehunters.messaging.infrastructure.entrypoint.scheduler;

import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Inbound scheduler that bounds outbox table growth by deleting PUBLISHED rows older than the
 * retention, in batches, for the adapter's origin. PENDING, IN_FLIGHT and FAILED rows are never
 * touched. Coordinated across instances by ShedLock so only one node purges at a time.
 */
@Slf4j
public class OutboxPurgeScheduler {

  private final OutboxRepositoryPort repository;
  private final OutboxPurgeSettings settings;

  /**
   * Creates the purge scheduler.
   *
   * @param repository the outbox repository scoped to one origin
   * @param settings the retention, batch size and clock
   */
  public OutboxPurgeScheduler(OutboxRepositoryPort repository, OutboxPurgeSettings settings) {
    this.repository = repository;
    this.settings = settings;
  }

  /**
   * Runs one purge cycle, deleting batches until a batch comes back short. Failures are logged by
   * exception class name only and never propagated, so the scheduler keeps running.
   */
  @Scheduled(
      fixedDelayString = "${outbox.purge.fixed-delay:3600000}",
      initialDelayString = "${outbox.purge.initial-delay:60000}")
  @SchedulerLock(
      name = "${outbox.purge.lock-name:outbox-purge-${spring.application.name:unknown}}",
      lockAtMostFor = "${outbox.purge.lock-at-most:PT10M}",
      lockAtLeastFor = "${outbox.purge.lock-at-least:PT30S}")
  public void purgePublished() {
    try {
      LocalDateTime cutoff = LocalDateTime.now(settings.clock()).minus(settings.retention());
      long total = purgeUntilShortBatch(cutoff);
      log.info("Outbox purge removed {} published messages older than {}", total, cutoff);
    } catch (RuntimeException e) {
      log.error("[ERROR] - outbox purge failed: {}", e.getClass().getName());
    }
  }

  private long purgeUntilShortBatch(LocalDateTime cutoff) {
    long total = 0;
    int deleted;
    do {
      deleted = repository.purgePublished(cutoff, settings.batchSize());
      total += deleted;
    } while (deleted == settings.batchSize());
    return total;
  }
}
