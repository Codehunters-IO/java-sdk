package io.codehunters.audit;

import io.codehunters.audit.persistence.AuditEventJdbcAdapter;
import io.codehunters.audit.persistence.AutoCommitDataSource;
import io.codehunters.audit.persistence.JsonSnapshotAuditEventPublisher;
import io.codehunters.audit.persistence.RequiresNewAuditEventPublisher;
import io.codehunters.audit.persistence.TransactionAwareAuditEventPublisher;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Boot auto-configuration that persists audit events to a relational database. Active when a
 * single {@link DataSource} candidate (one bean, or a primary one), Spring JDBC and Jackson are
 * present.
 *
 * <p>Its {@code auditTaskExecutor} and {@code auditJsonMapper} beans are not autowiring candidates,
 * so they never replace or collide with the application's own executor and mapper. Events are
 * written off the caller's thread unless {@code codehunters.audit.fail-on-error} is {@code true} or
 * {@code codehunters.audit.async.enabled} is {@code false}.
 *
 * <p>Inside a Spring transaction, the event of a failed call is written at once in a transaction of
 * its own, so it survives the rollback. The event of a successful call is written inside the
 * caller's transaction when {@code fail-on-error} is {@code true}; otherwise it is written after
 * the transaction completes, marked {@code ROLLED_BACK} if it did not commit. See {@link
 * TransactionAwareAuditEventPublisher}.
 */
@AutoConfiguration(
    before = AuditAutoConfiguration.class,
    afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnClass(
    name = {
      "org.springframework.jdbc.core.simple.JdbcClient",
      "org.springframework.transaction.PlatformTransactionManager",
      "tools.jackson.databind.json.JsonMapper"
    })
@ConditionalOnSingleCandidate(DataSource.class)
@EnableConfigurationProperties(AuditProperties.class)
public class AuditJdbcAutoConfiguration {

  private static final String EXECUTOR = "auditTaskExecutor";
  private static final String JSON_MAPPER = "auditJsonMapper";

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public AuditJdbcAutoConfiguration() {}

  /**
   * Provides the bounded executor that writes audit events. Shutdown waits up to {@code
   * async.await-termination} for queued events. The data source is a parameter only so that the
   * container destroys this executor, and drains its queue, before it closes the pool.
   *
   * @param properties the audit configuration
   * @param dataSource the data source the queued events are written to
   * @return the executor
   */
  @Bean(name = EXECUTOR, defaultCandidate = false)
  @ConditionalOnMissingBean(name = EXECUTOR)
  public ThreadPoolTaskExecutor auditTaskExecutor(
      AuditProperties properties, DataSource dataSource) {
    AuditProperties.Async async = properties.async();
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(async.corePoolSize());
    executor.setMaxPoolSize(async.maxPoolSize());
    executor.setQueueCapacity(async.queueCapacity());
    executor.setThreadNamePrefix("audit-");
    executor.setTaskDecorator(new AuditMdcTaskDecorator());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationMillis(async.awaitTermination().toMillis());
    return executor;
  }

  /**
   * Provides the {@link JsonMapper} that serializes audit payloads.
   *
   * @return a {@link JsonMapper}
   */
  @Bean(name = JSON_MAPPER, defaultCandidate = false)
  @ConditionalOnMissingBean(name = JSON_MAPPER)
  public JsonMapper auditJsonMapper() {
    return JsonMapper.builder().build();
  }

  /**
   * Registers the JDBC-backed audit event publisher. Payloads are copied to JSON on the caller's
   * thread; the write is synchronous when {@code fail-on-error} is {@code true} or async is
   * disabled, and on the {@code auditTaskExecutor} otherwise.
   *
   * @param dataSource the target data source
   * @param jsonMapper the JSON mapper for payload serialization
   * @param executor the executor for asynchronous publishing
   * @param properties the audit configuration
   * @param transactionManager the application's transaction manager, if it has exactly one
   * @param rejectionListener notified of events the executor refuses, if defined
   * @param failureListener notified of events that could not be written, if defined
   * @return the {@link AuditEventPublisherPort}
   */
  @Bean
  @ConditionalOnMissingBean(AuditEventPublisherPort.class)
  public AuditEventPublisherPort auditEventPublisher(
      DataSource dataSource,
      @Qualifier(JSON_MAPPER) JsonMapper jsonMapper,
      @Qualifier(EXECUTOR) Executor executor,
      AuditProperties properties,
      ObjectProvider<PlatformTransactionManager> transactionManager,
      ObjectProvider<AuditEventRejectionListener> rejectionListener,
      ObjectProvider<AuditEventFailureListener> failureListener) {
    var chain =
        new Chain(
            new AuditEventJdbcAdapter(dataSource, jsonMapper, true),
            isolatedWriter(dataSource, jsonMapper, transactionManager.getIfUnique()),
            new Listeners(
                rejectionListener.getIfUnique(() -> event -> {}),
                failureListener.getIfUnique(() -> event -> {})),
            executor);
    return new JsonSnapshotAuditEventPublisher(chain.routed(properties), jsonMapper);
  }

  private static AuditEventPublisherPort isolatedWriter(
      DataSource dataSource, JsonMapper jsonMapper, PlatformTransactionManager transactionManager) {
    if (transactionManager != null) {
      return new RequiresNewAuditEventPublisher(
          new AuditEventJdbcAdapter(dataSource, jsonMapper, true), transactionManager);
    }
    return new AuditEventJdbcAdapter(new AutoCommitDataSource(dataSource), jsonMapper, true);
  }

  private record Listeners(
      AuditEventRejectionListener rejected, AuditEventFailureListener failed) {}

  /** The writers an event can be routed to, depending on the configured mode. */
  private record Chain(
      AuditEventJdbcAdapter joined,
      AuditEventPublisherPort isolated,
      Listeners listeners,
      Executor executor) {

    AuditEventPublisherPort routed(AuditProperties properties) {
      if (properties.failOnError()) {
        return TransactionAwareAuditEventPublisher.atomic(joined, isolated);
      }
      if (!properties.async().enabled()) {
        return TransactionAwareAuditEventPublisher.deferred(failSafe(isolated));
      }
      return TransactionAwareAuditEventPublisher.deferred(
          new AsyncAuditEventPublisher(failSafe(joined), executor, listeners.rejected()));
    }

    private AuditEventPublisherPort failSafe(AuditEventPublisherPort writer) {
      return new FailSafeAuditEventPublisher(writer, listeners.failed());
    }
  }

  /** Counts lost events when Micrometer is on the classpath. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
  static class AuditMetricsConfiguration {

    /**
     * Increments {@code audit.events.rejected}, registered at startup on the application's {@link
     * MeterRegistry}.
     *
     * @param registry the meter registry, if the application defines exactly one
     * @return the listener
     */
    @Bean
    @ConditionalOnMissingBean
    AuditEventRejectionListener auditEventRejectionListener(
        ObjectProvider<MeterRegistry> registry) {
      Runnable rejected =
          increment(registry, "audit.events.rejected", "Audit events the executor refused");
      return event -> rejected.run();
    }

    /**
     * Increments {@code audit.events.failed}, registered at startup on the application's {@link
     * MeterRegistry}.
     *
     * @param registry the meter registry, if the application defines exactly one
     * @return the listener
     */
    @Bean
    @ConditionalOnMissingBean
    AuditEventFailureListener auditEventFailureListener(ObjectProvider<MeterRegistry> registry) {
      Runnable failed =
          increment(registry, "audit.events.failed", "Audit events that were not written");
      return event -> failed.run();
    }

    private static Runnable increment(
        ObjectProvider<MeterRegistry> registry, String name, String description) {
      MeterRegistry meters = registry.getIfUnique();
      if (meters == null) {
        return () -> {};
      }
      Counter counter = Counter.builder(name).description(description).register(meters);
      return counter::increment;
    }
  }
}
