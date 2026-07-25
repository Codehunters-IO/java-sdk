package io.codehunters.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.codehunters.messaging.application.port.SendOutboxMessagesPort;
import io.codehunters.messaging.domain.factory.EventMessageFactory;
import io.codehunters.messaging.domain.factory.OutboxEventFactory;
import io.codehunters.messaging.domain.model.EventMessage;
import io.codehunters.messaging.infrastructure.entrypoint.scheduler.OutboxScheduler;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import tools.jackson.databind.json.JsonMapper;

class EventMessagingAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(EventMessagingAutoConfiguration.class))
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
  void should_not_create_message_publisher_when_rabbit_template_missing() {
    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .run(
            context -> {
              assertThat(context).hasSingleBean(OutboxRepositoryPort.class);
              assertThat(context).doesNotHaveBean(MessagePublisherPort.class);
              assertThat(context).doesNotHaveBean(SendOutboxMessagesPort.class);
              assertThat(context).doesNotHaveBean(OutboxScheduler.class);
            });
  }

  @Test
  void should_back_off_when_custom_beans_provided() {
    var customFactory = new EventMessageFactory("custom-origin");

    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .withBean(EventMessageFactory.class, () -> customFactory)
        .run(
            context -> {
              assertThat(context).hasSingleBean(EventMessageFactory.class);
              assertThat(context.getBean(EventMessageFactory.class)).isSameAs(customFactory);
            });
  }

  @Test
  void should_not_create_outbox_beans_when_no_datasource() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(OutboxRepositoryPort.class);
          assertThat(context).doesNotHaveBean(SendOutboxMessagesPort.class);
        });
  }

  @Test
  void should_fallback_to_application_name_when_outbox_origin_not_set() {
    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .run(
            context -> {
              EventMessageFactory factory = context.getBean(EventMessageFactory.class);
              EventMessage<String> event =
                  factory.create("trace-1", "aggregate-1", "event.name", "EVENT_TYPE", "payload");
              assertThat(event.getHeader().getOrigin()).isEqualTo("test-app");
            });
  }

  private DataSource createDataSource() {
    return new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .addScript("db/test-schema.sql")
        .generateUniqueName(true)
        .build();
  }
}
