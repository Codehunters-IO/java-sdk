package io.codehunters.messaging.kafka.consumer.config;

import io.codehunters.messaging.json.EnvelopeFormat;
import io.codehunters.messaging.kafka.consumer.EnvelopeReader;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Boot auto-configuration for the consumer side of the event envelope. Loaded when the Kafka
 * client is on the classpath and {@code codehunters.messaging.consumer.enabled} is {@code true}
 * (the default). It does not depend on the outbox or on which transport the outbox publishes
 * through: a service can consume envelopes without writing any.
 *
 * <p>{@code inboundEnvelopeJsonMapper} is not a default candidate, so it never displaces nor
 * competes with the application's {@link JsonMapper}.
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
}
