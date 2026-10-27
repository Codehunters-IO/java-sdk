package io.codehunters.messaging.kafka.consumer.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.kafka.consumer.EnvelopeReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class EventMessagingKafkaConsumerAutoConfigurationTest {

  private static final String MAPPER = "inboundEnvelopeJsonMapper";

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
}
