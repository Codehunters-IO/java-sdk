package io.codehunters.messaging.config;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.infrastructure.drivenadapters.kafka.KafkaMessagePublisherAdapter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Spring Boot auto-configuration for the Kafka outbox publisher. Loaded only when {@code
 * spring-kafka} is on the classpath, {@code codehunters.outbox.enabled} is {@code true} (the
 * default) and Kafka is the outbox transport: {@code codehunters.outbox.publisher.type} is {@code
 * kafka}, or it is unset and {@code sdk-event-messaging-rabbit} is absent (see {@link
 * ConditionalOnOutboxTransport}).
 *
 * <p>The publisher sends through its own idempotent {@code outboxKafkaTemplate}, derived from the
 * application's {@link ProducerFactory} configuration, so the application's template and
 * serializers are never used for outbox records.
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
@ConditionalOnOutboxTransport("kafka")
@ConditionalOnBean(ProducerFactory.class)
public class EventMessagingKafkaAutoConfiguration {

  private static final String OUTBOX_PRODUCER_FACTORY = "outboxKafkaProducerFactory";
  private static final String OUTBOX_TEMPLATE = "outboxKafkaTemplate";

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public EventMessagingKafkaAutoConfiguration() {}

  /**
   * Provides the outbox's own producer factory, derived from the application's producer
   * configuration with String key and value serializers, {@code acks=all} and {@code
   * enable.idempotence=true}, and without a transactional id. It is not a default candidate, so it
   * never replaces the application's factory.
   *
   * @param applicationProducerFactory the application's producer factory
   * @return the outbox producer factory
   */
  @Bean(name = OUTBOX_PRODUCER_FACTORY, defaultCandidate = false)
  @ConditionalOnMissingBean(name = OUTBOX_PRODUCER_FACTORY)
  public DefaultKafkaProducerFactory<String, String> outboxKafkaProducerFactory(
      ProducerFactory<?, ?> applicationProducerFactory) {
    return new DefaultKafkaProducerFactory<>(
        OutboxProducerConfigs.from(applicationProducerFactory.getConfigurationProperties()));
  }

  /**
   * Provides the outbox's own {@link KafkaTemplate}, so an application value serializer (JSON, for
   * example) cannot re-encode the already serialized outbox payload. It is not a default candidate,
   * so it never replaces the application's template.
   *
   * @param outboxKafkaProducerFactory the outbox producer factory
   * @return the outbox template
   */
  @Bean(name = OUTBOX_TEMPLATE, defaultCandidate = false)
  @ConditionalOnMissingBean(name = OUTBOX_TEMPLATE)
  public KafkaTemplate<String, String> outboxKafkaTemplate(
      @Qualifier(OUTBOX_PRODUCER_FACTORY)
          DefaultKafkaProducerFactory<String, String> outboxKafkaProducerFactory) {
    return new KafkaTemplate<>(outboxKafkaProducerFactory);
  }

  /**
   * Provides the keyed Kafka message publisher.
   *
   * @param outboxKafkaTemplate the outbox template
   * @param properties the messaging configuration, whose Kafka claim budget is validated
   * @return a {@link MessagePublisherPort}
   */
  @Bean
  @ConditionalOnMissingBean(MessagePublisherPort.class)
  public MessagePublisherPort messagePublisherPort(
      @Qualifier(OUTBOX_TEMPLATE) KafkaTemplate<String, String> outboxKafkaTemplate,
      EventMessagingProperties properties) {
    properties.requireClaimBudget("kafka");
    return new KafkaMessagePublisherAdapter(
        outboxKafkaTemplate, properties.publisher().kafka().sendTimeout());
  }
}
