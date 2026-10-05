package io.codehunters.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.application.port.SendOutboxMessagesPort;
import io.codehunters.messaging.infrastructure.drivenadapters.kafka.KafkaMessagePublisherAdapter;
import io.codehunters.messaging.infrastructure.entrypoint.scheduler.OutboxScheduler;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

class EventMessagingKafkaAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  EventMessagingKafkaAutoConfiguration.class,
                  EventMessagingJdbcAutoConfiguration.class,
                  EventMessagingCoreAutoConfiguration.class,
                  OutboxDispatchSchedulerAutoConfiguration.class))
          .withPropertyValues("spring.application.name=test-app");

  @Test
  void should_select_kafka_publisher_when_publisher_type_is_kafka() {
    contextRunner
        .withClassLoader(new FilteredClassLoader("org.springframework.amqp"))
        .withPropertyValues("codehunters.outbox.publisher.type=kafka")
        .withBean(DataSource.class, this::createDataSource)
        .withBean(ProducerFactory.class, EventMessagingKafkaAutoConfigurationTest::producers)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context)
                  .getBean(MessagePublisherPort.class)
                  .isInstanceOf(KafkaMessagePublisherAdapter.class);
              assertThat(context).hasSingleBean(SendOutboxMessagesPort.class);
              assertThat(context).hasSingleBean(OutboxScheduler.class);
            });
  }

  @Test
  void should_select_kafka_publisher_when_type_unset_and_only_kafka_artifact_present() {
    contextRunner
        .withBean(ProducerFactory.class, EventMessagingKafkaAutoConfigurationTest::producers)
        .run(
            context ->
                assertThat(context)
                    .getBean(MessagePublisherPort.class)
                    .isInstanceOf(KafkaMessagePublisherAdapter.class));
  }

  @Test
  void should_build_idempotent_string_outbox_template_when_application_producer_uses_json() {
    contextRunner
        .withBean(ProducerFactory.class, EventMessagingKafkaAutoConfigurationTest::producers)
        .run(
            context ->
                assertThat(
                        context
                            .getBean("outboxKafkaTemplate", KafkaTemplate.class)
                            .getProducerFactory()
                            .getConfigurationProperties())
                    .containsEntry(
                        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
                    .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
                    .containsEntry(ProducerConfig.ACKS_CONFIG, "all"));
  }

  @Test
  void should_keep_outbox_template_out_of_type_injection_when_built() {
    contextRunner
        .withBean(ProducerFactory.class, EventMessagingKafkaAutoConfigurationTest::producers)
        .run(
            context ->
                assertThat(
                        ((AbstractBeanDefinition)
                                context.getBeanFactory().getBeanDefinition("outboxKafkaTemplate"))
                            .isDefaultCandidate())
                    .isFalse());
  }

  @Test
  void should_not_create_kafka_publisher_when_producer_factory_missing() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(MessagePublisherPort.class));
  }

  @Test
  void should_not_create_kafka_publisher_when_publisher_type_is_rabbit() {
    contextRunner
        .withPropertyValues("codehunters.outbox.publisher.type=rabbit")
        .withBean(ProducerFactory.class, EventMessagingKafkaAutoConfigurationTest::producers)
        .run(context -> assertThat(context).doesNotHaveBean(MessagePublisherPort.class));
  }

  @Test
  void should_fail_startup_naming_budget_when_type_unset_and_kafka_claim_budget_exceeded() {
    contextRunner
        .withPropertyValues("codehunters.outbox.claim.ttl-ms=1000")
        .withBean(ProducerFactory.class, EventMessagingKafkaAutoConfigurationTest::producers)
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("codehunters.outbox.publisher.kafka.send-timeout"));
  }

  @SuppressWarnings("unchecked")
  private static ProducerFactory<Object, Object> producers() {
    ProducerFactory<Object, Object> producerFactory = mock(ProducerFactory.class);
    when(producerFactory.getConfigurationProperties())
        .thenReturn(
            Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:9092",
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JacksonJsonSerializer.class));
    return producerFactory;
  }

  private DataSource createDataSource() {
    return new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .build();
  }
}
