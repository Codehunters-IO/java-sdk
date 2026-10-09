package io.codehunters.messaging.kafka.config;

import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.config.ConditionalOnOutboxTransport;
import io.codehunters.messaging.config.EventMessagingCoreAutoConfiguration;
import io.codehunters.messaging.config.EventMessagingProperties;
import io.codehunters.messaging.kafka.KafkaMessagePublisherAdapter;
import java.util.Map;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
 * serializers are never used for outbox records. The template does not enable Boot's observation:
 * the publisher writes the {@code traceparent} stored with each outbox row itself, so the consumer
 * continues the request's trace, not the dispatcher's.
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
  private static final String SEVERAL_FACTORIES =
      "The outbox Kafka publisher derives its producer from the application's ProducerFactory, but"
          + " there are several and none is @Primary: mark one @Primary or define a"
          + " DefaultKafkaProducerFactory<String, String> bean named "
          + OUTBOX_PRODUCER_FACTORY;
  private static final String HIDDEN_CONFIGURATION =
      "The application's ProducerFactory (%s) does not expose its configuration, which the outbox"
          + " Kafka publisher derives its producer from: define a"
          + " DefaultKafkaProducerFactory<String, String> bean named "
          + OUTBOX_PRODUCER_FACTORY;

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public EventMessagingKafkaAutoConfiguration() {}

  /**
   * Provides the outbox's own producer factory, derived from the application's producer
   * configuration with String key and value serializers, {@code acks=all} and {@code
   * enable.idempotence=true}, without a transactional id, with its own {@code client.id}, and with
   * every producer wait ({@code max.block.ms}, {@code delivery.timeout.ms}, {@code
   * request.timeout.ms}) bounded by {@code codehunters.outbox.publisher.kafka.send-timeout}.
   * Settings idempotence rejects are corrected and the result is parsed as the producer will, so an
   * invalid value fails startup instead of every dispatch. Boot's {@code
   * DefaultKafkaProducerFactoryCustomizer} beans (the Micrometer listener, for example) are
   * applied, after which the forced settings are applied again. It is not a default candidate, so
   * it never replaces the application's factory.
   *
   * @param applicationProducerFactories the application's producer factories; one, or one primary
   * @param properties the messaging configuration, whose Kafka send timeout bounds the producer
   * @param applicationName the application name, used in the outbox {@code client.id}
   * @param beanFactory the bean factory the Boot customizers are looked up in
   * @return the outbox producer factory
   * @throws IllegalStateException when the application has several producer factories and none is
   *     primary, or its factory does not expose its configuration
   */
  @Bean(name = OUTBOX_PRODUCER_FACTORY, defaultCandidate = false)
  @ConditionalOnMissingBean(name = OUTBOX_PRODUCER_FACTORY)
  public DefaultKafkaProducerFactory<String, String> outboxKafkaProducerFactory(
      ObjectProvider<ProducerFactory<?, ?>> applicationProducerFactories,
      EventMessagingProperties properties,
      @Value("${spring.application.name:unknown}") String applicationName,
      ListableBeanFactory beanFactory) {
    Map<String, Object> config =
        OutboxProducerConfigs.validated(
            OutboxProducerConfigs.from(
                applicationConfig(applicationProducerFactories),
                properties.publisher().kafka().sendTimeout(),
                applicationName));
    DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(config);
    OutboxProducerFactoryCustomizers.apply(beanFactory, factory);
    factory.updateConfigs(config);
    factory.setKeySerializer(new StringSerializer());
    factory.setValueSerializer(new StringSerializer());
    return factory;
  }

  private static Map<String, Object> applicationConfig(
      ObjectProvider<ProducerFactory<?, ?>> applicationProducerFactories) {
    ProducerFactory<?, ?> applicationFactory = applicationProducerFactories.getIfUnique();
    if (applicationFactory == null) {
      throw new IllegalStateException(SEVERAL_FACTORIES);
    }
    Map<String, Object> config = configurationOf(applicationFactory);
    if (config.isEmpty()) {
      throw new IllegalStateException(
          HIDDEN_CONFIGURATION.formatted(applicationFactory.getClass().getName()));
    }
    return config;
  }

  // ProducerFactory's default answers an empty map; other implementations may refuse instead.
  private static Map<String, Object> configurationOf(ProducerFactory<?, ?> applicationFactory) {
    try {
      return applicationFactory.getConfigurationProperties();
    } catch (UnsupportedOperationException e) {
      return Map.of();
    }
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
        outboxKafkaTemplate,
        properties.publisher().kafka().sendTimeout(),
        properties.publisher().legacyTraceparentHeader());
  }
}
