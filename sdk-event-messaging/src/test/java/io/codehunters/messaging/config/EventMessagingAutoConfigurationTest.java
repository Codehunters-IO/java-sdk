package io.codehunters.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.codehunters.messaging.application.port.SendOutboxMessagesPort;
import io.codehunters.messaging.domain.factory.EventMessageFactory;
import io.codehunters.messaging.domain.factory.OutboxEventFactory;
import io.codehunters.messaging.domain.model.EventMessage;
import io.codehunters.messaging.infrastructure.drivenadapters.jdbc.GenericOutboxDialect;
import io.codehunters.messaging.infrastructure.drivenadapters.jdbc.OutboxDialect;
import io.codehunters.messaging.infrastructure.drivenadapters.kafka.KafkaMessagePublisherAdapter;
import io.codehunters.messaging.infrastructure.drivenadapters.rabbitmq.RabbitMessagePublisherAdapter;
import io.codehunters.messaging.infrastructure.entrypoint.scheduler.OutboxPurgeScheduler;
import io.codehunters.messaging.infrastructure.entrypoint.scheduler.OutboxScheduler;
import io.codehunters.messaging.infrastructure.metrics.OutboxFailedGauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.kafka.core.KafkaTemplate;
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

  @Test
  void should_bind_default_properties_when_nothing_configured() {
    contextRunner.run(
        context -> {
          EventMessagingProperties props = context.getBean(EventMessagingProperties.class);
          assertThat(props.batchSize()).isEqualTo(50);
          assertThat(props.maxAttempts()).isEqualTo(5);
          assertThat(props.claim().ttlMs()).isEqualTo(300_000L);
          assertThat(props.claim().dialect()).isEqualTo("auto");
          assertThat(props.backoff().baseDelayMs()).isEqualTo(30_000L);
          assertThat(props.backoff().maxDelayMs()).isEqualTo(3_600_000L);
          assertThat(props.publisher().type()).isEqualTo("rabbit");
          assertThat(props.publisher().kafka().sendTimeout()).isEqualTo(Duration.ofSeconds(10));
          assertThat(props.purge().retention()).isEqualTo(Duration.ofDays(7));
          assertThat(props.purge().batchSize()).isEqualTo(500);
        });
  }

  @Test
  void should_bind_overridden_properties_when_configured() {
    contextRunner
        .withPropertyValues(
            "outbox.publisher.type=kafka",
            "outbox.publisher.kafka.send-timeout=3s",
            "outbox.purge.retention=3d",
            "outbox.purge.batch-size=100",
            "outbox.claim.dialect=postgresql")
        .run(
            context -> {
              EventMessagingProperties props = context.getBean(EventMessagingProperties.class);
              assertThat(props.publisher().type()).isEqualTo("kafka");
              assertThat(props.publisher().kafka().sendTimeout()).isEqualTo(Duration.ofSeconds(3));
              assertThat(props.purge().retention()).isEqualTo(Duration.ofDays(3));
              assertThat(props.purge().batchSize()).isEqualTo(100);
              assertThat(props.claim().dialect()).isEqualTo("postgresql");
            });
  }

  @Test
  void should_start_without_rabbit_on_classpath_when_spring_rabbit_absent() {
    contextRunner
        .withClassLoader(new FilteredClassLoader("org.springframework.amqp"))
        .withBean(DataSource.class, this::createDataSource)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(OutboxRepositoryPort.class);
              assertThat(context).hasSingleBean(LockProvider.class);
              assertThat(context).hasSingleBean(OutboxPurgeScheduler.class);
              assertThat(context).doesNotHaveBean(MessagePublisherPort.class);
            });
  }

  @Test
  @SuppressWarnings("unchecked")
  void should_select_kafka_publisher_when_publisher_type_is_kafka() {
    contextRunner
        .withClassLoader(new FilteredClassLoader("org.springframework.amqp"))
        .withPropertyValues("outbox.publisher.type=kafka")
        .withBean(DataSource.class, this::createDataSource)
        .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
        .run(
            context -> {
              assertThat(context)
                  .getBean(MessagePublisherPort.class)
                  .isInstanceOf(KafkaMessagePublisherAdapter.class);
              assertThat(context).hasSingleBean(SendOutboxMessagesPort.class);
              assertThat(context).hasSingleBean(OutboxScheduler.class);
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
  void should_keep_boot_json_mapper_primary_when_jackson_auto_configuration_present() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration.class, EventMessagingAutoConfiguration.class))
        .withBean(DataSource.class, this::createDataSource)
        .run(
            context -> {
              assertThat(context).hasBean("jacksonJsonMapper");
              assertThat(context).hasBean("outboxJsonMapper");
              assertThat(
                      context.getBeanFactory().getBeanDefinition("jacksonJsonMapper").isPrimary())
                  .isTrue();
              assertThat(context).hasSingleBean(OutboxRepositoryPort.class);
            });
  }

  @Test
  void should_provide_utc_clock_when_no_clock_defined() {
    contextRunner.run(
        context -> assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC));
  }

  @Test
  void should_use_user_clock_when_clock_bean_defined() {
    Clock custom = Clock.fixed(Instant.EPOCH, ZoneId.of("Europe/Madrid"));

    contextRunner
        .withBean(Clock.class, () -> custom)
        .run(context -> assertThat(context.getBean(Clock.class)).isSameAs(custom));
  }

  @Test
  void should_fail_startup_naming_property_when_claim_dialect_unknown() {
    contextRunner
        .withPropertyValues("outbox.claim.dialect=oracle")
        .withBean(DataSource.class, this::createDataSource)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .rootCause()
                  .hasMessageContaining("outbox.claim.dialect");
            });
  }

  @Test
  void should_resolve_generic_dialect_when_h2_and_dialect_auto() {
    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .run(
            context ->
                assertThat(context)
                    .getBean(OutboxDialect.class)
                    .isInstanceOf(GenericOutboxDialect.class));
  }

  @Test
  void should_register_failed_gauge_when_meter_registry_present() {
    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .run(context -> assertThat(context).hasSingleBean(OutboxFailedGauge.class));
  }

  @Test
  void should_start_without_gauge_when_micrometer_absent() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
        .withBean(DataSource.class, this::createDataSource)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(OutboxFailedGauge.class);
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
