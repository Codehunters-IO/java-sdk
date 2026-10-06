package io.codehunters.audit;

import io.codehunters.audit.persistence.AuditEventJdbcAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Boot auto-configuration that persists audit events to a relational database. Active when a
 * {@link DataSource} bean, Spring JDBC and Jackson are present.
 *
 * <p>Its {@code auditTaskExecutor} and {@code auditJsonMapper} beans are not autowiring candidates,
 * so they never replace or collide with the application's own executor and mapper. Events are
 * written off the caller's thread unless {@code codehunters.audit.fail-on-error} is {@code true} or
 * {@code codehunters.audit.async.enabled} is {@code false}.
 */
@AutoConfiguration(
    before = AuditAutoConfiguration.class,
    afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnClass(
    name = {
      "org.springframework.jdbc.core.simple.JdbcClient",
      "tools.jackson.databind.json.JsonMapper"
    })
@ConditionalOnBean(DataSource.class)
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
   * Registers the JDBC-backed audit event publisher, wrapped in an {@link AsyncAuditEventPublisher}
   * unless publishing must be synchronous.
   *
   * @param dataSource the target data source
   * @param jsonMapper the JSON mapper for payload serialization
   * @param executor the executor for asynchronous publishing
   * @param properties the audit configuration
   * @param rejectionListener notified of events the executor refuses, if defined
   * @return the {@link AuditEventPublisherPort}
   */
  @Bean
  @ConditionalOnMissingBean(AuditEventPublisherPort.class)
  public AuditEventPublisherPort auditEventPublisher(
      DataSource dataSource,
      @Qualifier(JSON_MAPPER) JsonMapper jsonMapper,
      @Qualifier(EXECUTOR) Executor executor,
      AuditProperties properties,
      ObjectProvider<AuditEventRejectionListener> rejectionListener) {
    var jdbc = new AuditEventJdbcAdapter(dataSource, jsonMapper, properties.failOnError());
    if (properties.failOnError() || !properties.async().enabled()) {
      return jdbc;
    }
    return new AsyncAuditEventPublisher(
        jdbc, executor, rejectionListener.getIfUnique(() -> event -> {}));
  }

  /** Counts rejected events when Micrometer is on the classpath. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
  static class AuditMetricsConfiguration {

    /**
     * Increments {@code audit.events.rejected} on the application's {@link MeterRegistry}.
     *
     * @param registry the meter registry, if the application defines exactly one
     * @return the listener
     */
    @Bean
    @ConditionalOnMissingBean
    AuditEventRejectionListener auditEventRejectionListener(
        ObjectProvider<MeterRegistry> registry) {
      return event ->
          registry.ifUnique(meters -> meters.counter("audit.events.rejected").increment());
    }
  }
}
