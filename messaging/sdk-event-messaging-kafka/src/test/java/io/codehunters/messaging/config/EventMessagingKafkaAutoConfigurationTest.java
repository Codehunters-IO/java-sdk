package io.codehunters.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.application.port.SendOutboxMessagesPort;
import io.codehunters.messaging.infrastructure.drivenadapters.kafka.KafkaMessagePublisherAdapter;
import io.codehunters.messaging.infrastructure.entrypoint.scheduler.OutboxScheduler;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.kafka.core.KafkaTemplate;

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
  @SuppressWarnings("unchecked")
  void should_select_kafka_publisher_when_publisher_type_is_kafka() {
    contextRunner
        .withClassLoader(new FilteredClassLoader("org.springframework.amqp"))
        .withPropertyValues("codehunters.outbox.publisher.type=kafka")
        .withBean(DataSource.class, this::createDataSource)
        .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
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
  @SuppressWarnings("unchecked")
  void should_select_kafka_publisher_when_type_unset_and_only_kafka_artifact_present() {
    contextRunner
        .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
        .run(
            context ->
                assertThat(context)
                    .getBean(MessagePublisherPort.class)
                    .isInstanceOf(KafkaMessagePublisherAdapter.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void should_not_create_kafka_publisher_when_publisher_type_is_rabbit() {
    contextRunner
        .withPropertyValues("codehunters.outbox.publisher.type=rabbit")
        .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
        .run(context -> assertThat(context).doesNotHaveBean(MessagePublisherPort.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void should_fail_startup_naming_budget_when_type_unset_and_kafka_claim_budget_exceeded() {
    contextRunner
        .withPropertyValues("codehunters.outbox.claim.ttl-ms=1000")
        .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("codehunters.outbox.publisher.kafka.send-timeout"));
  }

  private DataSource createDataSource() {
    return new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .build();
  }
}
