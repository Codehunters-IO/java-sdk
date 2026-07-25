package io.codehunters.messaging.config;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.codehunters.messaging.application.port.SendOutboxMessagesPort;
import io.codehunters.messaging.application.usecase.SendOutboxMessagesUseCase;
import io.codehunters.messaging.domain.factory.EventMessageFactory;
import io.codehunters.messaging.domain.factory.OutboxEventFactory;
import io.codehunters.messaging.infrastructure.drivenadapters.jdbc.OutboxJdbcAdapter;
import io.codehunters.messaging.infrastructure.drivenadapters.rabbitmq.RabbitMessagePublisherAdapter;
import io.codehunters.messaging.infrastructure.entrypoint.scheduler.OutboxScheduler;
import java.lang.management.ManagementFactory;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Boot auto-configuration for the transactional-outbox SDK. Wires the JDBC outbox
 * repository, the RabbitMQ publisher, the use case, the ShedLock-protected scheduler and supporting
 * factories. Active when {@code outbox.enabled} is {@code true} (the default).
 */
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
      "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration"
    })
@ConditionalOnProperty(
    prefix = "outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(EventMessagingProperties.class)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "60s")
public class EventMessagingAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public EventMessagingAutoConfiguration() {}

  /**
   * Provides the default Jackson message converter for RabbitMQ.
   *
   * @return a {@link JacksonJsonMessageConverter}
   */
  @Bean
  @ConditionalOnClass(JacksonJsonMessageConverter.class)
  @ConditionalOnMissingBean(JacksonJsonMessageConverter.class)
  public JacksonJsonMessageConverter jacksonJsonMessageConverter() {
    return new JacksonJsonMessageConverter();
  }

  /**
   * Provides the {@link RabbitTemplate}, attaching the trace-propagation post-processor when one is
   * available.
   *
   * <p><b>Note:</b> The {@link JacksonJsonMessageConverter} attached here is consumed only when
   * callers invoke {@link RabbitTemplate#convertAndSend} directly. The SDK's own {@link
   * RabbitMessagePublisherAdapter} uses {@link RabbitTemplate#send} with pre-built {@link
   * org.springframework.amqp.core.MessageProperties}, bypassing the converter. Do not remove the
   * converter: external SDK consumers may rely on it.
   *
   * @param connectionFactory the RabbitMQ connection factory
   * @param converter the message converter (used by external {@code convertAndSend} callers only)
   * @param tracePropagationMessagePostProcessor optional trace-propagation processor
   * @return a configured {@link RabbitTemplate}
   */
  @Bean
  @ConditionalOnClass(RabbitTemplate.class)
  @ConditionalOnBean(ConnectionFactory.class)
  @ConditionalOnMissingBean(RabbitTemplate.class)
  public RabbitTemplate rabbitTemplate(
      ConnectionFactory connectionFactory,
      JacksonJsonMessageConverter converter,
      ObjectProvider<MessagePostProcessor> tracePropagationMessagePostProcessor) {
    RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
    rabbitTemplate.setMessageConverter(converter);
    tracePropagationMessagePostProcessor.ifAvailable(
        rabbitTemplate::addBeforePublishPostProcessors);
    return rabbitTemplate;
  }

  /**
   * Provides the dedicated JSON mapper used to serialize outbox payloads.
   *
   * @return a {@link JsonMapper}
   */
  @Bean("outboxJsonMapper")
  @ConditionalOnMissingBean(name = "outboxJsonMapper")
  public JsonMapper outboxJsonMapper() {
    return JsonMapper.builder().build();
  }

  /**
   * Provides the event-message factory bound to the current application name.
   *
   * @param origin the originating application name
   * @return an {@link EventMessageFactory}
   */
  @Bean
  @ConditionalOnMissingBean(EventMessageFactory.class)
  public EventMessageFactory eventMessageFactory(
      @Value("${spring.application.name:unknown}") String origin) {
    return new EventMessageFactory(origin);
  }

  /**
   * Provides the outbox-message factory.
   *
   * @param eventMessageFactory the underlying event-message factory
   * @param origin the originating application name
   * @return an {@link OutboxEventFactory}
   */
  @Bean
  @ConditionalOnMissingBean(OutboxEventFactory.class)
  public OutboxEventFactory outboxEventFactory(
      EventMessageFactory eventMessageFactory,
      @Value("${spring.application.name:unknown}") String origin) {
    return new OutboxEventFactory(origin, eventMessageFactory);
  }

  /**
   * Provides the JDBC-backed outbox repository.
   *
   * @param dataSource the target data source
   * @param outboxJsonMapper the JSON mapper for outbox payloads
   * @param origin the originating application name
   * @return an {@link OutboxRepositoryPort}
   */
  @Bean
  @ConditionalOnMissingBean(OutboxRepositoryPort.class)
  @ConditionalOnBean(DataSource.class)
  public OutboxRepositoryPort outboxRepositoryPort(
      DataSource dataSource,
      JsonMapper outboxJsonMapper,
      @Value("${spring.application.name:unknown}") String origin) {
    return new OutboxJdbcAdapter(dataSource, outboxJsonMapper, origin);
  }

  /**
   * Provides the RabbitMQ-backed message publisher.
   *
   * @param rabbitTemplate the underlying {@link RabbitTemplate}
   * @return a {@link MessagePublisherPort}
   */
  @Bean
  @ConditionalOnMissingBean(MessagePublisherPort.class)
  @ConditionalOnBean(RabbitTemplate.class)
  public MessagePublisherPort messagePublisherPort(RabbitTemplate rabbitTemplate) {
    return new RabbitMessagePublisherAdapter(rabbitTemplate);
  }

  /**
   * Provides the use case that claims and publishes pending outbox messages.
   *
   * @param outboxRepositoryPort the outbox repository
   * @param messagePublisherPort the message publisher
   * @param properties the messaging configuration
   * @param applicationName the originating application name (used as instance prefix)
   * @return a {@link SendOutboxMessagesPort}
   */
  @Bean
  @ConditionalOnMissingBean(SendOutboxMessagesPort.class)
  @ConditionalOnBean({OutboxRepositoryPort.class, MessagePublisherPort.class})
  public SendOutboxMessagesPort sendOutboxMessagesPort(
      OutboxRepositoryPort outboxRepositoryPort,
      MessagePublisherPort messagePublisherPort,
      EventMessagingProperties properties,
      @Value("${spring.application.name:unknown}") String applicationName) {
    return new SendOutboxMessagesUseCase(
        outboxRepositoryPort,
        messagePublisherPort,
        properties.batchSize(),
        properties.maxAttempts(),
        resolveInstanceId(applicationName),
        applicationName,
        properties.claim().ttlMs(),
        properties.backoff().baseDelayMs(),
        properties.backoff().maxDelayMs());
  }

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
            .withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(dataSource))
            .usingDbTime()
            .build());
  }

  private String resolveInstanceId(String applicationName) {
    String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
    return applicationName + "@" + runtimeName;
  }
}
