package io.codehunters.messaging.infrastructure.metrics;

import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Registers the {@code outbox_failed_messages} gauge, tagged by {@code origin}, which reports the
 * number of FAILED outbox rows of that origin. FAILED rows are kept for manual requeue and never
 * purged, so a non-zero value needs operator attention.
 */
public class OutboxFailedGauge implements MeterBinder {

  /** Gauge name. */
  public static final String METRIC_NAME = "outbox_failed_messages";

  private final OutboxRepositoryPort repository;
  private final String origin;

  /**
   * Creates the gauge binder.
   *
   * @param repository the outbox repository scoped to {@code origin}
   * @param origin the origin used as the {@code origin} tag
   */
  public OutboxFailedGauge(OutboxRepositoryPort repository, String origin) {
    this.repository = repository;
    this.origin = origin;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder(METRIC_NAME, repository, OutboxRepositoryPort::countFailed)
        .tag("origin", origin)
        .description("Number of FAILED outbox messages awaiting manual requeue")
        .register(registry);
  }
}
