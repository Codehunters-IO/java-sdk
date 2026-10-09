package io.codehunters.messaging.jdbc.config;

import io.codehunters.messaging.OutboxDispatchStore;
import io.codehunters.messaging.OutboxWriter;
import io.codehunters.messaging.config.EventMessagingCoreAutoConfiguration;
import io.codehunters.messaging.jdbc.OutboxAdapterSettings;
import io.codehunters.messaging.jdbc.OutboxJdbcAdapter;
import java.time.Clock;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Boot auto-configuration for the PostgreSQL outbox store. Wires the dedicated JSON mapper,
 * the JDBC outbox store and the ShedLock provider. Active when {@code codehunters.outbox.enabled}
 * is {@code true} (the default).
 *
 * <p>Runs before {@link EventMessagingCoreAutoConfiguration} so the repository is visible to the
 * use case's {@code @ConditionalOnBean}, and after Boot's Jackson auto-configuration. The dedicated
 * {@code outboxJsonMapper} is not a default candidate, so it never displaces nor competes with the
 * application's {@link JsonMapper}. The dispatch and purge tasks, which need the use case, live in
 * {@link OutboxDispatchSchedulerAutoConfiguration}, and the FAILED-row gauge, which needs the
 * store, in {@link OutboxMetricsAutoConfiguration}.
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
   * Provides the JDBC outbox store, which is both the application's {@link OutboxWriter} and the
   * dispatcher's {@link OutboxDispatchStore}. Backs off when the application defines either. The
   * mapper is injected by qualifier so Boot's primary {@link JsonMapper} is never used for, nor
   * displaced by, outbox payloads. Startup fails when the data source reports a database other than
   * PostgreSQL.
   *
   * @param dataSource the target data source
   * @param outboxJsonMapper the dedicated JSON mapper for outbox payloads
   * @param clock the SDK's private {@code outboxClock}
   * @param origin the originating application name
   * @return the {@link OutboxJdbcAdapter}
   */
  @Bean
  @ConditionalOnMissingBean({OutboxWriter.class, OutboxDispatchStore.class})
  @ConditionalOnBean(DataSource.class)
  public OutboxJdbcAdapter outboxJdbcAdapter(
      DataSource dataSource,
      @Qualifier("outboxJsonMapper") JsonMapper outboxJsonMapper,
      @Qualifier("outboxClock") Clock clock,
      @Value("${spring.application.name:unknown}") String origin) {
    OutboxDatabaseCheck.requirePostgres(dataSource);
    return new OutboxJdbcAdapter(
        dataSource, outboxJsonMapper, new OutboxAdapterSettings(origin, clock));
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
}
