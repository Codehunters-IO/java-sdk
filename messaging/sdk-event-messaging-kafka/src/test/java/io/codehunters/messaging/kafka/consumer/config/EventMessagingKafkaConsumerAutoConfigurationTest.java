package io.codehunters.messaging.kafka.consumer.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.kafka.consumer.EnvelopeReader;
import io.codehunters.messaging.kafka.consumer.InboundRetryTopics;
import io.codehunters.messaging.kafka.consumer.SanitizedDeadLetters;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.retrytopic.RetryTopicComponentFactory;
import org.springframework.kafka.retrytopic.RetryTopicSchedulerWrapper;
import tools.jackson.databind.json.JsonMapper;

class EventMessagingKafkaConsumerAutoConfigurationTest {

  private static final String MAPPER = "inboundEnvelopeJsonMapper";
  private static final String FORWARDING_TEMPLATE = "inboundForwardingKafkaTemplate";
  private static final String FORWARDING_FACTORY = "inboundForwardingProducerFactory";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(EventMessagingKafkaConsumerAutoConfiguration.class));

  @Test
  void should_register_reader_and_inbound_mapper_when_kafka_is_on_classpath() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(EnvelopeReader.class);
          assertThat(context).getBean(MAPPER).isInstanceOf(JsonMapper.class);
        });
  }

  @Test
  void should_not_offer_inbound_mapper_as_default_candidate_when_registered() {
    contextRunner.run(
        context ->
            assertThat(
                    ((AbstractBeanDefinition) context.getBeanFactory().getBeanDefinition(MAPPER))
                        .isDefaultCandidate())
                .isFalse());
  }

  @Test
  void should_keep_application_mapper_unique_when_inbound_mapper_is_registered() {
    JsonMapper application = JsonMapper.builder().build();

    contextRunner
        .withBean(JsonMapper.class, () -> application)
        .run(context -> assertThat(context.getBean(JsonMapper.class)).isSameAs(application));
  }

  @Test
  void should_not_register_reader_when_consumer_is_disabled() {
    contextRunner
        .withPropertyValues("codehunters.messaging.consumer.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(EnvelopeReader.class));
  }

  @Test
  void should_keep_user_reader_when_one_is_declared() {
    EnvelopeReader custom = new EnvelopeReader(JsonMapper.builder().build());

    contextRunner
        .withBean(EnvelopeReader.class, () -> custom)
        .run(context -> assertThat(context.getBean(EnvelopeReader.class)).isSameAs(custom));
  }

  @Test
  void should_start_without_outbox_transport_when_rabbit_is_absent() {
    contextRunner
        .withClassLoader(new FilteredClassLoader("org.springframework.amqp"))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(EnvelopeReader.class);
            });
  }

  @Test
  void should_not_register_reader_when_kafka_clients_are_absent() {
    contextRunner
        .withClassLoader(new FilteredClassLoader("org.apache.kafka.clients.consumer"))
        .run(context -> assertThat(context).doesNotHaveBean(EnvelopeReader.class));
  }

  @Test
  void should_register_sanitized_dead_letters_when_kafka_is_on_classpath() {
    contextRunner.run(
        context ->
            assertThat(context.getBean(RetryTopicComponentFactory.class))
                .isInstanceOf(SanitizedDeadLetters.class));
  }

  @Test
  void should_keep_user_component_factory_when_one_is_declared() {
    RetryTopicComponentFactory custom = new RetryTopicComponentFactory();

    contextRunner
        .withBean(RetryTopicComponentFactory.class, () -> custom)
        .run(
            context ->
                assertThat(context.getBean(RetryTopicComponentFactory.class)).isSameAs(custom));
  }

  @Test
  void should_register_own_retry_scheduler_when_kafka_is_on_classpath() {
    contextRunner.run(
        context -> assertThat(context).hasSingleBean(RetryTopicSchedulerWrapper.class));
  }

  @Test
  void should_register_retry_topics_when_application_has_producer_factory() {
    contextRunner
        .withBean(
            ProducerFactory.class, EventMessagingKafkaConsumerAutoConfigurationTest::producers)
        .run(context -> assertThat(context).hasSingleBean(InboundRetryTopics.class));
  }

  @Test
  void should_not_offer_forwarding_template_as_default_candidate_when_registered() {
    contextRunner
        .withBean(
            ProducerFactory.class, EventMessagingKafkaConsumerAutoConfigurationTest::producers)
        .run(
            context ->
                assertThat(
                        ((AbstractBeanDefinition)
                                context.getBeanFactory().getBeanDefinition(FORWARDING_TEMPLATE))
                            .isDefaultCandidate())
                    .isFalse());
  }

  @Test
  void should_keep_application_template_unique_when_forwarding_template_is_registered() {
    KafkaTemplate<Object, Object> application = new KafkaTemplate<>(producers());

    contextRunner
        .withBean(
            ProducerFactory.class, EventMessagingKafkaConsumerAutoConfigurationTest::producers)
        .withBean(KafkaTemplate.class, () -> application)
        .run(context -> assertThat(context.getBean(KafkaTemplate.class)).isSameAs(application));
  }

  @Test
  void should_forward_with_idempotent_string_producer_when_registered() {
    contextRunner
        .withBean(
            ProducerFactory.class, EventMessagingKafkaConsumerAutoConfigurationTest::producers)
        .run(
            context ->
                assertThat(
                        context
                            .getBean(FORWARDING_FACTORY, DefaultKafkaProducerFactory.class)
                            .getConfigurationProperties())
                    .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                    .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
                    .containsEntry(
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                    .doesNotContainKey(ProducerConfig.TRANSACTIONAL_ID_CONFIG));
  }

  @Test
  void should_not_register_retry_topics_when_application_has_no_producer_factory() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(InboundRetryTopics.class));
  }

  @Test
  void should_keep_reader_without_retry_beans_when_spring_kafka_is_absent() {
    contextRunner
        .withClassLoader(new FilteredClassLoader("org.springframework.kafka"))
        .run(
            context -> {
              assertThat(context).hasSingleBean(EnvelopeReader.class);
              assertThat(context).doesNotHaveBean("retryTopicComponentFactory");
            });
  }

  private static ProducerFactory<Object, Object> producers() {
    return new DefaultKafkaProducerFactory<>(
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            "localhost:9092",
            ProducerConfig.TRANSACTIONAL_ID_CONFIG,
            "tx-",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class));
  }
}
