package io.codehunters.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.codehunters.messaging.application.port.SendOutboxMessagesPort;
import io.codehunters.messaging.infrastructure.drivenadapters.kafka.KafkaMessagePublisherAdapter;
import io.codehunters.messaging.infrastructure.drivenadapters.rabbitmq.RabbitMessagePublisherAdapter;
import io.codehunters.messaging.infrastructure.entrypoint.scheduler.OutboxScheduler;
import java.util.Map;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.kafka.core.ProducerFactory;

class EventMessagingRabbitAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  EventMessagingRabbitAutoConfiguration.class,
                  EventMessagingJdbcAutoConfiguration.class,
                  EventMessagingCoreAutoConfiguration.class,
                  OutboxDispatchSchedulerAutoConfiguration.class))
          .withPropertyValues("spring.application.name=test-app")
          .withClassLoader(new FilteredClassLoader(EventMessagingKafkaAutoConfiguration.class));

  private final ApplicationContextRunner bothTransportsRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  EventMessagingRabbitAutoConfiguration.class,
                  EventMessagingKafkaAutoConfiguration.class,
                  EventMessagingCoreAutoConfiguration.class))
          .withBean(ConnectionFactory.class, () -> connectionFactory(true, true))
          .withBean(ProducerFactory.class, EventMessagingRabbitAutoConfigurationTest::producers);

  @Test
  void should_create_all_beans_when_datasource_and_rabbit_available() {
    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .withBean(ConnectionFactory.class, () -> connectionFactory(true, true))
        .run(
            context -> {
              assertThat(context).hasSingleBean(OutboxRepositoryPort.class);
              assertThat(context).hasSingleBean(MessagePublisherPort.class);
              assertThat(context).hasSingleBean(SendOutboxMessagesPort.class);
              assertThat(context).hasSingleBean(OutboxScheduler.class);
              assertThat(context).hasSingleBean(LockProvider.class);
            });
  }

  @Test
  void should_select_rabbit_publisher_when_type_unset_and_only_rabbit_artifact_present() {
    contextRunner
        .withBean(ConnectionFactory.class, () -> connectionFactory(true, true))
        .run(
            context ->
                assertThat(context)
                    .getBean(MessagePublisherPort.class)
                    .isInstanceOf(RabbitMessagePublisherAdapter.class));
  }

  @Test
  void should_build_mandatory_outbox_template_when_connection_factory_present() {
    contextRunner
        .withBean(ConnectionFactory.class, () -> connectionFactory(true, true))
        .run(
            context ->
                assertThat(
                        context
                            .getBean("outboxRabbitTemplate", RabbitTemplate.class)
                            .isMandatoryFor(null))
                    .isTrue());
  }

  @Test
  void should_build_outbox_template_when_connection_factory_uses_correlated_confirms() {
    contextRunner
        .withBean(ConnectionFactory.class, EventMessagingRabbitAutoConfigurationTest::correlated)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasBean("outboxRabbitTemplate");
            });
  }

  @Test
  void should_publish_on_publisher_connection_when_outbox_template_built() {
    contextRunner
        .withBean(ConnectionFactory.class, () -> connectionFactory(true, true))
        .run(
            context ->
                assertThat(
                        context
                            .getBean("outboxRabbitTemplate", RabbitTemplate.class)
                            .isUsePublisherConnection())
                    .isTrue());
  }

  @Test
  void should_keep_outbox_template_out_of_type_injection_when_application_has_none() {
    contextRunner
        .withBean(ConnectionFactory.class, () -> connectionFactory(true, true))
        .run(
            context -> {
              assertThat(
                      ((AbstractBeanDefinition)
                              context.getBeanFactory().getBeanDefinition("outboxRabbitTemplate"))
                          .isDefaultCandidate())
                  .isFalse();
              assertThat(context).doesNotHaveBean(MessageConverter.class);
            });
  }

  @Test
  void should_leave_application_rabbit_template_untouched_when_outbox_template_built() {
    RabbitTemplate applicationTemplate = new RabbitTemplate(connectionFactory(false, false));

    contextRunner
        .withBean(ConnectionFactory.class, () -> connectionFactory(true, true))
        .withBean("rabbitTemplate", RabbitTemplate.class, () -> applicationTemplate)
        .run(
            context -> {
              assertThat(context.getBean(RabbitTemplate.class)).isSameAs(applicationTemplate);
              assertThat(applicationTemplate.isMandatoryFor(null)).isFalse();
            });
  }

  @Test
  void should_fail_startup_naming_settings_when_publisher_confirms_disabled() {
    contextRunner
        .withBean(ConnectionFactory.class, () -> connectionFactory(false, true))
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("spring.rabbitmq.publisher-confirm-type=simple")
                    .hasMessageContaining("correlated")
                    .hasMessageContaining("spring.rabbitmq.publisher-returns=true"));
  }

  @Test
  void should_fail_startup_naming_settings_when_publisher_returns_disabled() {
    contextRunner
        .withBean(ConnectionFactory.class, () -> connectionFactory(true, false))
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("spring.rabbitmq.publisher-returns=true"));
  }

  @Test
  void should_not_create_rabbit_publisher_when_connection_factory_missing() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(MessagePublisherPort.class));
  }

  @Test
  void should_not_create_rabbit_publisher_when_publisher_type_is_kafka() {
    contextRunner
        .withPropertyValues("codehunters.outbox.publisher.type=kafka")
        .withBean(ConnectionFactory.class, () -> connectionFactory(true, true))
        .run(context -> assertThat(context).doesNotHaveBean(MessagePublisherPort.class));
  }

  @Test
  void should_fail_startup_naming_budget_when_type_unset_and_rabbit_claim_budget_exceeded() {
    contextRunner
        .withPropertyValues("codehunters.outbox.claim.ttl-ms=1000")
        .withBean(ConnectionFactory.class, () -> connectionFactory(true, true))
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("codehunters.outbox.publisher.rabbit.confirm-timeout"));
  }

  @Test
  void should_fail_startup_naming_type_key_when_both_transports_present_and_type_unset() {
    bothTransportsRunner.run(
        context ->
            assertThat(context.getStartupFailure())
                .rootCause()
                .hasMessageContaining("codehunters.outbox.publisher.type"));
  }

  @Test
  void should_select_rabbit_publisher_when_both_transports_present_and_type_rabbit() {
    bothTransportsRunner
        .withPropertyValues("codehunters.outbox.publisher.type=rabbit")
        .run(
            context ->
                assertThat(context)
                    .getBean(MessagePublisherPort.class)
                    .isInstanceOf(RabbitMessagePublisherAdapter.class));
  }

  @Test
  void should_select_kafka_publisher_when_both_transports_present_and_type_kafka() {
    bothTransportsRunner
        .withPropertyValues("codehunters.outbox.publisher.type=KAFKA")
        .run(
            context ->
                assertThat(context)
                    .getBean(MessagePublisherPort.class)
                    .isInstanceOf(KafkaMessagePublisherAdapter.class));
  }

  private static ConnectionFactory connectionFactory(boolean confirms, boolean returns) {
    ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    when(connectionFactory.isSimplePublisherConfirms()).thenReturn(confirms);
    when(connectionFactory.isPublisherReturns()).thenReturn(returns);
    return connectionFactory;
  }

  private static ConnectionFactory correlated() {
    ConnectionFactory connectionFactory = connectionFactory(false, true);
    when(connectionFactory.isPublisherConfirms()).thenReturn(true);
    return connectionFactory;
  }

  @SuppressWarnings("unchecked")
  private static ProducerFactory<Object, Object> producers() {
    ProducerFactory<Object, Object> producerFactory = mock(ProducerFactory.class);
    when(producerFactory.getConfigurationProperties())
        .thenReturn(Map.of("bootstrap.servers", "localhost:9092"));
    return producerFactory;
  }

  private DataSource createDataSource() {
    return new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .build();
  }
}
