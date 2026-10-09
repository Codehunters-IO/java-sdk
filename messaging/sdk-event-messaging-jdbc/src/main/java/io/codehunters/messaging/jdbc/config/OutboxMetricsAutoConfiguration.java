package io.codehunters.messaging.jdbc.config;

import io.codehunters.messaging.OutboxDispatchStore;
import io.codehunters.messaging.OutboxWriter;
import io.codehunters.messaging.jdbc.metrics.OutboxFailedGauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the {@code outbox_failed_messages} gauge. Active when
 * Micrometer is on the classpath and {@code codehunters.outbox.enabled} is {@code true} (the
 * default).
 *
 * <p>Separate from {@link EventMessagingJdbcAutoConfiguration} because the gauge needs an {@link
 * OutboxDispatchStore}, which that configuration may or may not create: an application that defines
 * its own {@link OutboxWriter} makes it back off, leaving no store to count. Running after it, the
 * gauge is conditioned on the store itself.
 */
@AutoConfiguration(after = EventMessagingJdbcAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(
    prefix = "codehunters.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxMetricsAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public OutboxMetricsAutoConfiguration() {}

  /**
   * Provides the {@code outbox_failed_messages} gauge for this origin.
   *
   * @param outboxDispatchStore the outbox store
   * @param origin the originating application name
   * @param clock the SDK's private {@code outboxClock}, which ages the cached count
   * @return an {@link OutboxFailedGauge}
   */
  @Bean
  @ConditionalOnMissingBean(OutboxFailedGauge.class)
  @ConditionalOnBean(OutboxDispatchStore.class)
  public OutboxFailedGauge outboxFailedGauge(
      OutboxDispatchStore outboxDispatchStore,
      @Value("${spring.application.name:unknown}") String origin,
      @Qualifier("outboxClock") Clock clock) {
    return new OutboxFailedGauge(outboxDispatchStore, origin, clock);
  }
}
