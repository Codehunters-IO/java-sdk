package io.codehunters.messaging.kafka.consumer.config;

import io.codehunters.messaging.json.EnvelopeFormat;
import io.codehunters.messaging.kafka.consumer.DeadLetterHook;
import io.codehunters.messaging.kafka.consumer.EnvelopeReader;
import io.codehunters.messaging.kafka.consumer.InboundRetryTopics;
import io.codehunters.messaging.kafka.consumer.SanitizedDeadLetters;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.retrytopic.RetryTopicComponentFactory;
import org.springframework.kafka.retrytopic.RetryTopicSchedulerWrapper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Boot auto-configuration for the consumer side of the event envelope. Loaded when the Kafka
 * client is on the classpath and {@code codehunters.messaging.consumer.enabled} is {@code true}
 * (the default). It does not depend on the outbox or on which transport the outbox publishes
 * through: a service can consume envelopes without writing any.
 *
 * <p>{@code inboundEnvelopeJsonMapper} is not a default candidate, so it never displaces nor
 * competes with the application's {@link JsonMapper}.
 *
 * <p>With {@code spring-kafka} on the classpath it also provides what non-blocking retry topics
 * need: {@link SanitizedDeadLetters} as the {@link RetryTopicComponentFactory} (unless the
 * application declares one), an SDK-owned {@link RetryTopicSchedulerWrapper}, and, when the
 * application has a {@link ProducerFactory}, the forwarding template and {@link
 * InboundRetryTopics}.
 */
@AutoConfiguration(
    afterName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")
@ConditionalOnClass(ConsumerRecord.class)
@ConditionalOnProperty(
    prefix = "codehunters.messaging.consumer",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class EventMessagingKafkaConsumerAutoConfiguration {

  private static final String INBOUND_MAPPER = "inboundEnvelopeJsonMapper";

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public EventMessagingKafkaConsumerAutoConfiguration() {}

  /**
   * Provides the mapper inbound envelopes are read with, built from the application's {@link
   * JsonMapper.Builder} when there is one (Boot's, with every {@code JsonMapperBuilderCustomizer}
   * applied), so payloads bind with the application's modules, and from a plain builder otherwise.
   * The envelope is pinned by {@link EnvelopeFormat}, the same definition the outbox writes with.
   * It is not a default candidate, so injecting a {@link JsonMapper} by type never sees it.
   *
   * @param builders the application's mapper builder, if any
   * @return the inbound envelope mapper
   */
  @Bean(name = INBOUND_MAPPER, defaultCandidate = false)
  @ConditionalOnMissingBean(name = INBOUND_MAPPER)
  public JsonMapper inboundEnvelopeJsonMapper(ObjectProvider<JsonMapper.Builder> builders) {
    return EnvelopeFormat.pin(builders.getIfAvailable(JsonMapper::builder).build().rebuild())
        .build();
  }

  /**
   * Provides the reader that turns String Kafka records into envelopes with their dedupe ids.
   *
   * @param inboundEnvelopeJsonMapper the inbound envelope mapper
   * @return an {@link EnvelopeReader}
   */
  @Bean
  @ConditionalOnMissingBean
  public EnvelopeReader envelopeReader(
      @Qualifier(INBOUND_MAPPER) JsonMapper inboundEnvelopeJsonMapper) {
    return new EnvelopeReader(inboundEnvelopeJsonMapper);
  }

  /** Retry-topic components; needs {@code spring-kafka}. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(RetryTopicComponentFactory.class)
  static class InboundRetryConfiguration {

    private static final String RETRY_THREAD_PREFIX = "inbound-retry-";

    /**
     * Provides the retry-topic components that dead-letter without exception messages and call the
     * {@link DeadLetterHook} beans, in bean order.
     *
     * @param hooks the dead-letter hooks
     * @return the component factory
     */
    @Bean
    @ConditionalOnMissingBean(RetryTopicComponentFactory.class)
    SanitizedDeadLetters retryTopicComponentFactory(ObjectProvider<DeadLetterHook> hooks) {
      return new SanitizedDeadLetters(hooks.orderedStream().toList());
    }

    /**
     * Provides the scheduler retry back-off runs on, owned by the SDK, so it never depends on how
     * many {@code TaskScheduler} beans the application has. The scheduler is not a bean.
     *
     * @return the scheduler wrapper
     */
    @Bean
    @ConditionalOnMissingBean
    RetryTopicSchedulerWrapper retryTopicSchedulerWrapper() {
      ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
      scheduler.setPoolSize(1);
      scheduler.setThreadNamePrefix(RETRY_THREAD_PREFIX);
      return new RetryTopicSchedulerWrapper(scheduler);
    }
  }

  /** The producer failed records are forwarded with; derived from the application's. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(RetryTopicComponentFactory.class)
  @ConditionalOnBean(ProducerFactory.class)
  static class InboundForwardingConfiguration {

    private static final String FORWARDING_FACTORY = "inboundForwardingProducerFactory";
    private static final String FORWARDING_TEMPLATE = "inboundForwardingKafkaTemplate";
    private static final String SEVERAL_FACTORIES =
        "Inbound retry topics derive their forwarding producer from the application's"
            + " ProducerFactory, but there are several and none is @Primary: mark one @Primary or"
            + " define a DefaultKafkaProducerFactory<String, String> bean named "
            + FORWARDING_FACTORY;

    /**
     * Provides the forwarding producer factory: the application's producer configuration with
     * String serializers, {@code acks=all}, idempotence and no transactional id. It is not a
     * default candidate, so it never replaces the application's factory.
     *
     * @param applicationProducerFactories the application's producer factories; one, or a primary
     * @param applicationName the application name, used in the client id
     * @return the forwarding producer factory
     * @throws IllegalStateException when there are several factories and none is primary
     */
    @Bean(name = FORWARDING_FACTORY, defaultCandidate = false)
    @ConditionalOnMissingBean(name = FORWARDING_FACTORY)
    DefaultKafkaProducerFactory<String, String> inboundForwardingProducerFactory(
        ObjectProvider<ProducerFactory<?, ?>> applicationProducerFactories,
        @Value("${spring.application.name:unknown}") String applicationName) {
      ProducerFactory<?, ?> application = applicationProducerFactories.getIfUnique();
      if (application == null) {
        throw new IllegalStateException(SEVERAL_FACTORIES);
      }
      Map<String, Object> config =
          ForwardingProducerConfigs.from(application.getConfigurationProperties(), applicationName);
      return new DefaultKafkaProducerFactory<>(
          config, new StringSerializer(), new StringSerializer());
    }

    /**
     * Provides the template failed records are forwarded with. It is not a default candidate, so it
     * never replaces the application's template.
     *
     * @param inboundForwardingProducerFactory the forwarding producer factory
     * @return the forwarding template
     */
    @Bean(name = FORWARDING_TEMPLATE, defaultCandidate = false)
    @ConditionalOnMissingBean(name = FORWARDING_TEMPLATE)
    KafkaTemplate<String, String> inboundForwardingKafkaTemplate(
        @Qualifier(FORWARDING_FACTORY)
            DefaultKafkaProducerFactory<String, String> inboundForwardingProducerFactory) {
      return new KafkaTemplate<>(inboundForwardingProducerFactory);
    }

    /**
     * Provides the factory of per-topic retry configurations.
     *
     * @param inboundForwardingKafkaTemplate the forwarding template
     * @return the retry topics factory
     */
    @Bean
    @ConditionalOnMissingBean
    InboundRetryTopics inboundRetryTopics(
        @Qualifier(FORWARDING_TEMPLATE)
            KafkaTemplate<String, String> inboundForwardingKafkaTemplate) {
      return new InboundRetryTopics(inboundForwardingKafkaTemplate);
    }
  }
}
