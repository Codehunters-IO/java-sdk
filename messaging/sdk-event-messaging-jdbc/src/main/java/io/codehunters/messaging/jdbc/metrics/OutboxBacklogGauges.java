package io.codehunters.messaging.jdbc.metrics;

import io.codehunters.messaging.OutboxBacklog;
import io.codehunters.messaging.OutboxDispatchStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Registers two gauges, tagged by {@code origin}, that show whether dispatch keeps up:
 *
 * <ul>
 *   <li>{@code outbox_pending_messages}: PENDING and IN_FLIGHT rows of that origin;
 *   <li>{@code outbox_oldest_pending_age_seconds}: how long ago the oldest of them was written, 0
 *       when none waits.
 * </ul>
 *
 * <p>A held dispatch lock, a key blocked by a FAILED row or a broker that refuses all show up as an
 * age that keeps growing, long before consumers notice. Both are read from the table in one query
 * at most once per {@link OutboxFailedGauge#CACHE_TTL}; scrapes in between report the cached
 * values. A store that cannot report its backlog leaves both at 0.
 */
public class OutboxBacklogGauges implements MeterBinder {

  /** Name of the pending-count gauge. */
  public static final String PENDING = "outbox_pending_messages";

  /** Name of the oldest-pending-age gauge. */
  public static final String OLDEST_AGE = "outbox_oldest_pending_age_seconds";

  private static final OutboxBacklog EMPTY = new OutboxBacklog(0, Optional.empty());

  private final OutboxDispatchStore store;
  private final String origin;
  private final Clock clock;
  private OutboxBacklog cached = EMPTY;
  private Instant refreshAt = Instant.MIN;

  /**
   * Creates the gauge binder.
   *
   * @param store the outbox store scoped to {@code origin}
   * @param origin the origin used as the {@code origin} tag
   * @param clock the clock that ages the cached values and measures the age
   */
  public OutboxBacklogGauges(OutboxDispatchStore store, String origin, Clock clock) {
    this.store = store;
    this.origin = origin;
    this.clock = clock;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder(PENDING, this, OutboxBacklogGauges::pending)
        .tag("origin", origin)
        .description("Outbox messages waiting to be published (PENDING or IN_FLIGHT)")
        .strongReference(true)
        .register(registry);
    Gauge.builder(OLDEST_AGE, this, OutboxBacklogGauges::oldestAgeSeconds)
        .tag("origin", origin)
        .description("Age of the oldest outbox message waiting to be published")
        .baseUnit("seconds")
        .strongReference(true)
        .register(registry);
  }

  private double pending() {
    return backlog().pending();
  }

  private double oldestAgeSeconds() {
    return backlog()
        .oldestOccurredAt()
        .map(oldest -> Math.max(0, Duration.between(oldest, clock.instant()).toMillis()) / 1000.0)
        .orElse(0.0);
  }

  private synchronized OutboxBacklog backlog() {
    Instant now = clock.instant();
    if (!now.isBefore(refreshAt)) {
      cached = store.backlog().orElse(EMPTY);
      refreshAt = now.plus(OutboxFailedGauge.CACHE_TTL);
    }
    return cached;
  }
}
