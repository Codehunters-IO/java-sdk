package io.codehunters.messaging.jdbc.scheduler;

import io.codehunters.messaging.OutboxDispatchStore;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One purge run that bounds outbox table growth by deleting PUBLISHED rows older than the
 * retention, in batches, for the adapter's origin. PENDING, IN_FLIGHT and FAILED rows are never
 * touched. Run every {@code codehunters.outbox.purge.fixed-delay} by {@link
 * OutboxSchedulingLifecycle} under the {@code purge.lock-name} ShedLock lock, so only one node
 * purges at a time.
 */
public class OutboxPurgeScheduler implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(OutboxPurgeScheduler.class);

  private final OutboxDispatchStore repository;
  private final OutboxPurgeSettings settings;

  /**
   * Creates the purge scheduler.
   *
   * @param repository the outbox repository scoped to one origin
   * @param settings the retention, batch size and clock
   */
  public OutboxPurgeScheduler(OutboxDispatchStore repository, OutboxPurgeSettings settings) {
    this.repository = repository;
    this.settings = settings;
  }

  /**
   * Runs one purge cycle, deleting batches until a batch comes back short. Failures are logged by
   * exception class name only and never propagated, so the scheduler keeps running.
   */
  @Override
  public void run() {
    try {
      Instant cutoff = settings.clock().instant().minus(settings.retention());
      long total = purgeUntilShortBatch(cutoff);
      log.info("Outbox purge removed {} published messages older than {}", total, cutoff);
    } catch (RuntimeException e) {
      log.error("[ERROR] - outbox purge failed: {}", e.getClass().getName());
    }
  }

  private long purgeUntilShortBatch(Instant cutoff) {
    long total = 0;
    int deleted;
    do {
      deleted = repository.purgePublished(cutoff, settings.batchSize());
      total += deleted;
    } while (deleted == settings.batchSize());
    return total;
  }
}
