package io.codehunters.messaging.jdbc.scheduler;

import java.time.Clock;
import java.time.Duration;

/**
 * Purge job settings, grouped as a parameter object so the {@link OutboxPurgeScheduler} constructor
 * stays within the three-parameter rule.
 *
 * @param retention how long PUBLISHED rows are kept before they become purgeable; must be positive
 * @param batchSize the maximum number of rows deleted per statement; must be at least 1
 * @param clock the clock the cutoff is computed from; UTC, like the adapter's timestamps
 */
public record OutboxPurgeSettings(Duration retention, int batchSize, Clock clock) {

  /** Validates the settings. */
  public OutboxPurgeSettings {
    if (retention == null || retention.isNegative() || retention.isZero()) {
      throw new IllegalArgumentException("Outbox purge retention must be positive");
    }
    if (batchSize < 1) {
      throw new IllegalArgumentException("Outbox purge batch size must be at least 1");
    }
    if (clock == null) {
      throw new IllegalArgumentException("Outbox purge clock is required");
    }
  }
}
