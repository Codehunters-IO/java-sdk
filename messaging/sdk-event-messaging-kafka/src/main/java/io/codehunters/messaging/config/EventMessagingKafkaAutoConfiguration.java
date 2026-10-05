package io.codehunters.messaging.config;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.infrastructure.drivenadapters.kafka.KafkaMessagePublisherAdapter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Spring Boot auto-configuration for the Kafka outbox publisher. Loaded only when {@code
 * spring-kafka} is on the classpath, {@code codehunters.outbox.enabled} is {@code true} (the default) and
 * {@code codehunters.outbox.publisher.type} is {@code kafka}.
 *
 * <p>Runs before {@link EventMessagingCoreAutoConfiguration} so the publisher is visible to the use
 * case's {@code @ConditionalOnBean}.
 */
@AutoConfiguration(
    before = EventMessagingCoreAutoConfiguration.class,
    afterName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(
    prefix = "codehunters.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnProperty(prefix = "codehunters.outbox.publisher", name = "type", havingValue = "kafka")
public class EventMessagingKafkaAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public EventMessagingKafkaAutoConfiguration() {}

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
