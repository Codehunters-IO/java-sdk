package io.codehunters.audit;

import io.codehunters.audit.persistence.AuditEventJdbcAdapter;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Boot auto-configuration that persists audit events to a relational database. Active when a
 * {@link DataSource} bean is present; wires an async executor, a {@link JsonMapper} and the JDBC
 * {@link AuditEventPublisherPort}.
 */
@AutoConfiguration(
    before = AuditAutoConfiguration.class,
    afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnBean(DataSource.class)
@EnableAsync
@EnableConfigurationProperties(AuditProperties.class)
public class AuditJdbcAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public AuditJdbcAutoConfiguration() {}

  /**
   * Provides the executor used to publish audit events asynchronously.
   *
   * @return a configured {@link Executor}
   */
  @Bean
  @ConditionalOnMissingBean(name = "auditTaskExecutor")
  public Executor auditTaskExecutor() {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("audit-");
    executor.initialize();
    return executor;
  }

  /**
   * Provides the {@link JsonMapper} used to serialize audit payloads.
   *
   * @return a {@link JsonMapper}
   */
  @Bean("auditJsonMapper")
  @ConditionalOnMissingBean(JsonMapper.class)
  public JsonMapper auditJsonMapper() {
    return JsonMapper.builder().build();
  }

  /**
   * Registers the JDBC-backed audit event publisher.
   *
   * @param dataSource the target data source
   * @param jsonMapper the JSON mapper for payload serialization
   * @param properties the audit configuration
   * @return a JDBC {@link AuditEventPublisherPort}
   */
  @Bean
  @ConditionalOnMissingBean(AuditEventPublisherPort.class)
  public AuditEventPublisherPort auditEventJdbcAdapter(
      DataSource dataSource, JsonMapper jsonMapper, AuditProperties properties) {
    return new AuditEventJdbcAdapter(dataSource, jsonMapper, properties.failOnError());
  }
}
