package io.codehunters.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.codehunters.messaging.application.port.SendOutboxMessagesPort;
import io.codehunters.messaging.domain.factory.EventMessageFactory;
import io.codehunters.messaging.domain.factory.OutboxEventFactory;
import io.codehunters.messaging.infrastructure.drivenadapters.rabbitmq.RabbitMessagePublisherAdapter;
import io.codehunters.messaging.infrastructure.entrypoint.scheduler.OutboxScheduler;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import tools.jackson.databind.json.JsonMapper;

class EventMessagingRabbitAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  EventMessagingRabbitAutoConfiguration.class,
                  EventMessagingJdbcAutoConfiguration.class,
                  EventMessagingCoreAutoConfiguration.class,
                  OutboxDispatchSchedulerAutoConfiguration.class))
          .withPropertyValues("spring.application.name=test-app");

  @Test
  void should_create_all_beans_when_datasource_and_rabbit_available() {
    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(JsonMapper.class);
              assertThat(context).hasSingleBean(EventMessageFactory.class);
              assertThat(context).hasSingleBean(OutboxEventFactory.class);
              assertThat(context).hasSingleBean(OutboxRepositoryPort.class);
              assertThat(context).hasSingleBean(MessagePublisherPort.class);
              assertThat(context).hasSingleBean(SendOutboxMessagesPort.class);
              assertThat(context).hasSingleBean(OutboxScheduler.class);
              assertThat(context).hasSingleBean(LockProvider.class);
            });
  }

  @Test
  void should_select_rabbit_publisher_when_publisher_type_not_set() {
    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
        .run(
            context ->
                assertThat(context)
                    .getBean(MessagePublisherPort.class)
                    .isInstanceOf(RabbitMessagePublisherAdapter.class));
  }

  @Test
  void should_create_rabbit_template_when_connection_factory_present() {
    contextRunner
        .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
        .withBean(MessagePostProcessor.class, () -> message -> message)
        .run(
            context -> {
              assertThat(context).hasSingleBean(RabbitTemplate.class);
              assertThat(context)
                  .getBean(MessagePublisherPort.class)
                  .isInstanceOf(RabbitMessagePublisherAdapter.class);
            });
  }

  @Test
  void should_not_create_rabbit_publisher_when_publisher_type_is_kafka() {
    contextRunner
        .withPropertyValues("codehunters.outbox.publisher.type=kafka")
        .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
        .run(context -> assertThat(context).doesNotHaveBean(MessagePublisherPort.class));
  }

  private DataSource createDataSource() {
    return new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .build();
  }
}
