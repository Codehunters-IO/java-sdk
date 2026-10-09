package io.codehunters.messaging.jdbc.metrics;

import io.codehunters.messaging.OutboxRepositoryPort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Registers the {@code outbox_failed_messages} gauge, tagged by {@code origin}, which reports the
 * number of FAILED outbox rows of that origin. FAILED rows are kept for manual requeue and never
 * purged, so a non-zero value needs operator attention.
 *
 * <p>The count is read from the table at most once per {@link #CACHE_TTL}; scrapes in between
 * report the cached value, so frequent or parallel scrapes do not each run a count query.
 */
public class OutboxFailedGauge implements MeterBinder {

  /** Gauge name. */
  public static final String METRIC_NAME = "outbox_failed_messages";

  /** How long a FAILED-row count is reused before the table is queried again. */
  public static final Duration CACHE_TTL = Duration.ofSeconds(30);

  private final OutboxRepositoryPort repository;
  private final String origin;
  private final Clock clock;
  private long cachedCount;
  private Instant refreshAt = Instant.MIN;

  /**
   * Creates the gauge binder.
   *
   * @param repository the outbox repository scoped to {@code origin}
   * @param origin the origin used as the {@code origin} tag
   * @param clock the clock that ages the cached count
   */
  public OutboxFailedGauge(OutboxRepositoryPort repository, String origin, Clock clock) {
    this.repository = repository;
    this.origin = origin;
    this.clock = clock;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder(METRIC_NAME, this, OutboxFailedGauge::failedCount)
        .tag("origin", origin)
        .description("Number of FAILED outbox messages awaiting manual requeue")
        .strongReference(true)
        .register(registry);
  }

  private synchronized double failedCount() {
    Instant now = clock.instant();
    if (!now.isBefore(refreshAt)) {
      cachedCount = repository.countFailed();
      refreshAt = now.plus(CACHE_TTL);
    }
    return cachedCount;
  }
}
