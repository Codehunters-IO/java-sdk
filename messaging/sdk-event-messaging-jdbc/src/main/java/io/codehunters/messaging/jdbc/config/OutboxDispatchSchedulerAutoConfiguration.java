package io.codehunters.messaging.jdbc.config;

import io.codehunters.messaging.SendOutboxMessagesPort;
import io.codehunters.messaging.config.EventMessagingCoreAutoConfiguration;
import io.codehunters.messaging.jdbc.scheduler.OutboxScheduler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the ShedLock-protected outbox dispatch scheduler. Active when
 * {@code codehunters.outbox.enabled} and {@code codehunters.outbox.scheduling.enabled} are {@code
 * true} (both default); with scheduling off, the application triggers {@link
 * SendOutboxMessagesPort#execute()} itself.
 *
 * <p>Separate from {@link EventMessagingJdbcAutoConfiguration} because it must run after {@link
 * EventMessagingCoreAutoConfiguration}, which creates the {@link SendOutboxMessagesPort} this
 * scheduler triggers, while the JDBC configuration must run before it.
 */
@AutoConfiguration(after = EventMessagingCoreAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "codehunters.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnProperty(
    prefix = "codehunters.outbox.scheduling",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxDispatchSchedulerAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public OutboxDispatchSchedulerAutoConfiguration() {}

  /**
   * Provides the scheduled job that periodically triggers outbox dispatch.
   *
   * @param sendOutboxMessagesPort the dispatch port
   * @return an {@link OutboxScheduler}
   */
  @Bean
  @ConditionalOnMissingBean(OutboxScheduler.class)
  @ConditionalOnBean(SendOutboxMessagesPort.class)
  public OutboxScheduler outboxScheduler(SendOutboxMessagesPort sendOutboxMessagesPort) {
    return new OutboxScheduler(sendOutboxMessagesPort);
  }
}
