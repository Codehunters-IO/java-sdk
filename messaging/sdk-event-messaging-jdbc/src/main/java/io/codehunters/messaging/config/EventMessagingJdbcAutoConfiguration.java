package io.codehunters.messaging.config;

import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.codehunters.messaging.infrastructure.drivenadapters.jdbc.OutboxAdapterSettings;
import io.codehunters.messaging.infrastructure.drivenadapters.jdbc.OutboxDialect;
import io.codehunters.messaging.infrastructure.drivenadapters.jdbc.OutboxDialectResolver;
import io.codehunters.messaging.infrastructure.drivenadapters.jdbc.OutboxJdbcAdapter;
import io.codehunters.messaging.infrastructure.entrypoint.scheduler.OutboxPurgeScheduler;
import io.codehunters.messaging.infrastructure.entrypoint.scheduler.OutboxPurgeSettings;
import io.codehunters.messaging.infrastructure.metrics.OutboxFailedGauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Boot auto-configuration for the JDBC outbox store. Wires the dedicated JSON mapper, the
 * claim dialect, the JDBC outbox repository, the ShedLock provider, the purge scheduler and the
 * FAILED-row gauge. Active when {@code codehunters.outbox.enabled} is {@code true} (the default).
 * Scheduling ({@code @EnableScheduling}, {@code @EnableSchedulerLock}) and the purge scheduler are
 * registered only while {@code codehunters.outbox.scheduling.enabled} is {@code true} (the
 * default), so an application that runs dispatch and purge itself, or in another process, can turn
 * them off.
 *
 * <p>Runs before {@link EventMessagingCoreAutoConfiguration} so the repository is visible to the
 * use case's {@code @ConditionalOnBean}, and after Boot's Jackson auto-configuration. The dedicated
 * {@code outboxJsonMapper} is not a default candidate, so it never displaces nor competes with the
 * application's {@link JsonMapper}. The dispatch scheduler, which needs the use case, lives in
 * {@link OutboxDispatchSchedulerAutoConfiguration}.
 */
@AutoConfiguration(
    before = EventMessagingCoreAutoConfiguration.class,
    afterName = {
      "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
      "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"
    })
@ConditionalOnProperty(
    prefix = "codehunters.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class EventMessagingJdbcAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public EventMessagingJdbcAutoConfiguration() {}

  /**
   * Provides the dedicated JSON mapper used to serialize outbox payloads. It is not a default
   * candidate, so injecting a {@link JsonMapper} by type never sees it and Boot's or the
   * application's mapper stays the only match; the repository asks for it by qualifier.
   *
   * @return a {@link JsonMapper}
   */
  @Bean(name = "outboxJsonMapper", defaultCandidate = false)
  @ConditionalOnMissingBean(name = "outboxJsonMapper")
  public JsonMapper outboxJsonMapper() {
    return JsonMapper.builder().build();
  }

  /**
   * Resolves the claim dialect from {@code codehunters.outbox.claim.dialect}. Startup fails when
   * the value is unknown or {@code auto} meets an unsupported database. Skipped when the
   * application supplies its own {@link OutboxRepositoryPort}.
   *
   * @param dataSource the outbox data source
   * @param properties the messaging configuration
   * @return the resolved {@link OutboxDialect}
   */
  @Bean
  @ConditionalOnMissingBean({OutboxDialect.class, OutboxRepositoryPort.class})
  @ConditionalOnBean(DataSource.class)
  public OutboxDialect outboxDialect(DataSource dataSource, EventMessagingProperties properties) {
    return OutboxDialectResolver.resolve(dataSource, properties.claim().dialect());
  }

  /**
   * Provides the JDBC-backed outbox repository. The mapper is injected by qualifier so Boot's
   * primary {@link JsonMapper} is never used for, nor displaced by, outbox payloads.
   *
   * @param dataSource the target data source
   * @param outboxJsonMapper the dedicated JSON mapper for outbox payloads
   * @param dialect the resolved claim dialect
   * @param clock the outbox clock
   * @param origin the originating application name
   * @return an {@link OutboxRepositoryPort}
   */
  @Bean
  @ConditionalOnMissingBean(OutboxRepositoryPort.class)
  @ConditionalOnBean(DataSource.class)
  public OutboxRepositoryPort outboxRepositoryPort(
      DataSource dataSource,
      @Qualifier("outboxJsonMapper") JsonMapper outboxJsonMapper,
      OutboxDialect dialect,
      Clock clock,
      @Value("${spring.application.name:unknown}") String origin) {
    return new OutboxJdbcAdapter(
        dataSource, outboxJsonMapper, new OutboxAdapterSettings(origin, dialect, clock));
  }

  /**
   * Provides the scheduled job that deletes PUBLISHED rows older than {@code
   * codehunters.outbox.purge.retention}.
   *
   * @param outboxRepositoryPort the outbox repository
   * @param properties the messaging configuration
   * @param clock the outbox clock
   * @return an {@link OutboxPurgeScheduler}
   */
  @Bean
  @ConditionalOnMissingBean(OutboxPurgeScheduler.class)
  @ConditionalOnBean(OutboxRepositoryPort.class)
  @ConditionalOnProperty(
      prefix = "codehunters.outbox.scheduling",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public OutboxPurgeScheduler outboxPurgeScheduler(
      OutboxRepositoryPort outboxRepositoryPort, EventMessagingProperties properties, Clock clock) {
    EventMessagingProperties.Purge purge = properties.purge();
    return new OutboxPurgeScheduler(
        outboxRepositoryPort, new OutboxPurgeSettings(purge.retention(), purge.batchSize(), clock));
  }

  /**
   * Provides a JDBC-template ShedLock provider so multiple instances coordinate dispatch.
   *
   * @param dataSource the target data source
   * @return a {@link LockProvider}
   */
  @Bean
  @ConditionalOnMissingBean(LockProvider.class)
  @ConditionalOnBean(DataSource.class)
  public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(new JdbcTemplate(dataSource))
            .usingDbTime()
            .build());
  }

  /**
   * Turns on Spring scheduling and ShedLock for the dispatch and purge schedulers. Skipped when
   * {@code codehunters.outbox.scheduling.enabled} is {@code false}.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(
      prefix = "codehunters.outbox.scheduling",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  @EnableScheduling
  @EnableSchedulerLock(defaultLockAtMostFor = "60s")
  static class OutboxSchedulingConfiguration {}

  /**
   * FAILED-row gauge wiring. Loaded only when Micrometer is on the classpath.
   *
   * <p>Nested configurations are registered before the outer class's bean methods, so the gauge is
   * conditioned on the {@link DataSource} that triggers the repository rather than on {@link
   * OutboxRepositoryPort} itself, which would not be visible yet.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(MeterRegistry.class)
  static class OutboxMetricsConfiguration {

    /**
     * Provides the {@code outbox_failed_messages} gauge for this origin.
     *
     * @param outboxRepositoryPort the outbox repository
     * @param origin the originating application name
     * @return an {@link OutboxFailedGauge}
     */
    @Bean
    @ConditionalOnMissingBean(OutboxFailedGauge.class)
    @ConditionalOnBean(DataSource.class)
    public OutboxFailedGauge outboxFailedGauge(
        OutboxRepositoryPort outboxRepositoryPort,
        @Value("${spring.application.name:unknown}") String origin) {
      return new OutboxFailedGauge(outboxRepositoryPort, origin);
    }
  }
}
