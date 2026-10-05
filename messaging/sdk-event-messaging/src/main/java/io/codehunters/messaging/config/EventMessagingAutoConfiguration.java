package io.codehunters.messaging.config;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.codehunters.messaging.application.port.SendOutboxMessagesPort;
import io.codehunters.messaging.application.usecase.SendOutboxMessagesUseCase;
import io.codehunters.messaging.domain.factory.EventMessageFactory;
import io.codehunters.messaging.domain.factory.OutboxEventFactory;
import io.codehunters.messaging.infrastructure.drivenadapters.jdbc.OutboxAdapterSettings;
import io.codehunters.messaging.infrastructure.drivenadapters.jdbc.OutboxDialect;
import io.codehunters.messaging.infrastructure.drivenadapters.jdbc.OutboxDialectResolver;
import io.codehunters.messaging.infrastructure.drivenadapters.jdbc.OutboxJdbcAdapter;
import io.codehunters.messaging.infrastructure.drivenadapters.kafka.KafkaMessagePublisherAdapter;
import io.codehunters.messaging.infrastructure.drivenadapters.rabbitmq.RabbitMessagePublisherAdapter;
import io.codehunters.messaging.infrastructure.entrypoint.scheduler.OutboxPurgeScheduler;
import io.codehunters.messaging.infrastructure.entrypoint.scheduler.OutboxPurgeSettings;
import io.codehunters.messaging.infrastructure.entrypoint.scheduler.OutboxScheduler;
import io.codehunters.messaging.infrastructure.metrics.OutboxFailedGauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.management.ManagementFactory;
import java.time.Clock;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Boot auto-configuration for the transactional-outbox SDK. Wires the JDBC outbox
 * repository, the claim dialect, the UTC clock, the use case, the ShedLock-protected dispatch and
 * purge schedulers and supporting factories. Active when {@code outbox.enabled} is {@code true}
 * (the default).
 *
 * <p>Broker-specific beans live in nested configurations guarded by {@code @ConditionalOnClass}, so
 * a service that ships only one broker client (for example Kafka without {@code spring-rabbit})
 * still starts. The publisher is selected by {@code outbox.publisher.type} ({@code rabbit} by
 * default, or {@code kafka}). The configuration runs after Boot's Jackson auto-configuration so the
 * dedicated {@code outboxJsonMapper} never displaces Boot's primary {@link JsonMapper}.
 */
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
      "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
      "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
      "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"
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
   * Provides the UTC clock used for outbox timestamps, backoff and purge cut-offs. An
   * application-defined {@link Clock} bean takes precedence.
   *
   * @return the system UTC clock
   */
  @Bean
  @ConditionalOnMissingBean(Clock.class)
  public Clock outboxClock() {
    return Clock.systemUTC();
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
   * Resolves the claim dialect from {@code outbox.claim.dialect}. Startup fails when the value is
   * unknown or {@code auto} meets an unsupported database. Skipped when the application supplies
   * its own {@link OutboxRepositoryPort}.
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
   * Provides the use case that claims and publishes pending outbox messages.
   *
   * @param outboxRepositoryPort the outbox repository
   * @param messagePublisherPort the message publisher
   * @param properties the messaging configuration
   * @param applicationName the originating application name (used as instance prefix)
   * @param clock the outbox clock
   * @return a {@link SendOutboxMessagesPort}
   */
  @Bean
  @ConditionalOnMissingBean(SendOutboxMessagesPort.class)
  @ConditionalOnBean({OutboxRepositoryPort.class, MessagePublisherPort.class})
  public SendOutboxMessagesPort sendOutboxMessagesPort(
      OutboxRepositoryPort outboxRepositoryPort,
      MessagePublisherPort messagePublisherPort,
      EventMessagingProperties properties,
      @Value("${spring.application.name:unknown}") String applicationName,
      Clock clock) {
    return new SendOutboxMessagesUseCase(
        outboxRepositoryPort,
        messagePublisherPort,
        properties.batchSize(),
        properties.maxAttempts(),
        resolveInstanceId(applicationName),
        applicationName,
        properties.claim().ttlMs(),
        properties.backoff().baseDelayMs(),
        properties.backoff().maxDelayMs(),
        clock);
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
   * Provides the scheduled job that deletes PUBLISHED rows older than {@code
   * outbox.purge.retention}.
   *
   * @param outboxRepositoryPort the outbox repository
   * @param properties the messaging configuration
   * @param clock the outbox clock
   * @return an {@link OutboxPurgeScheduler}
   */
  @Bean
  @ConditionalOnMissingBean(OutboxPurgeScheduler.class)
  @ConditionalOnBean(OutboxRepositoryPort.class)
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

  private String resolveInstanceId(String applicationName) {
    String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
    return applicationName + "@" + runtimeName;
  }

  /**
   * RabbitMQ publisher wiring. Loaded only when {@code spring-rabbit} is on the classpath and
   * {@code outbox.publisher.type} is {@code rabbit} or unset.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(RabbitTemplate.class)
  @ConditionalOnProperty(
      prefix = "outbox.publisher",
      name = "type",
      havingValue = "rabbit",
      matchIfMissing = true)
  static class RabbitPublisherConfiguration {

    /**
     * Provides the default Jackson message converter for RabbitMQ.
     *
     * @return a {@link JacksonJsonMessageConverter}
     */
    @Bean
    @ConditionalOnMissingBean(JacksonJsonMessageConverter.class)
    public JacksonJsonMessageConverter jacksonJsonMessageConverter() {
      return new JacksonJsonMessageConverter();
    }

    /**
     * Provides the {@link RabbitTemplate}, attaching the trace-propagation post-processor when one
     * is available.
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
  }

  /**
   * Kafka publisher wiring. Loaded only when {@code spring-kafka} is on the classpath and {@code
   * outbox.publisher.type} is {@code kafka}.
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(KafkaTemplate.class)
  @ConditionalOnProperty(prefix = "outbox.publisher", name = "type", havingValue = "kafka")
  static class KafkaPublisherConfiguration {

    /**
     * Provides the keyed Kafka message publisher.
     *
     * @param kafkaTemplate the application's {@link KafkaTemplate}
     * @param properties the messaging configuration
     * @return a {@link MessagePublisherPort}
     */
    @Bean
    @ConditionalOnMissingBean(MessagePublisherPort.class)
    @ConditionalOnBean(KafkaTemplate.class)
    public MessagePublisherPort messagePublisherPort(
        KafkaTemplate<String, String> kafkaTemplate, EventMessagingProperties properties) {
      return new KafkaMessagePublisherAdapter(
          kafkaTemplate, properties.publisher().kafka().sendTimeout());
    }
  }

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
