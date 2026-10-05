package io.codehunters.messaging.config;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.codehunters.messaging.application.port.SendOutboxMessagesPort;
import io.codehunters.messaging.application.usecase.SendOutboxMessagesUseCase;
import io.codehunters.messaging.domain.factory.EventMessageFactory;
import io.codehunters.messaging.domain.factory.OutboxEventFactory;
import java.lang.management.ManagementFactory;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the transport-neutral part of the transactional-outbox SDK.
 * Binds {@link EventMessagingProperties} and wires the UTC clock, the message factories and the
 * dispatch use case. Active when {@code codehunters.outbox.enabled} is {@code true} (the default).
 *
 * <p>The use case needs an {@link OutboxRepositoryPort} and a {@link MessagePublisherPort}, which
 * come from {@code sdk-event-messaging-jdbc} and a transport artifact ({@code
 * sdk-event-messaging-rabbit} or {@code sdk-event-messaging-kafka}). This configuration therefore
 * runs after their auto-configurations, referenced by name because any of them may be absent.
 */
@AutoConfiguration(
    afterName = {
      "io.codehunters.messaging.config.EventMessagingJdbcAutoConfiguration",
      "io.codehunters.messaging.config.EventMessagingRabbitAutoConfiguration",
      "io.codehunters.messaging.config.EventMessagingKafkaAutoConfiguration"
    })
@ConditionalOnProperty(
    prefix = "codehunters.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(EventMessagingProperties.class)
public class EventMessagingCoreAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public EventMessagingCoreAutoConfiguration() {}

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

  private String resolveInstanceId(String applicationName) {
    String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
    return applicationName + "@" + runtimeName;
  }
}
