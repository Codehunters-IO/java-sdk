package io.codehunters.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.codehunters.messaging.application.port.SendOutboxMessagesPort;
import io.codehunters.messaging.domain.factory.EventMessageFactory;
import io.codehunters.messaging.domain.factory.OutboxEventFactory;
import io.codehunters.messaging.domain.model.EventMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EventMessagingCoreAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(EventMessagingCoreAutoConfiguration.class))
          .withPropertyValues("spring.application.name=test-app");

  @Test
  void should_create_factories_when_nothing_configured() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(EventMessageFactory.class);
          assertThat(context).hasSingleBean(OutboxEventFactory.class);
        });
  }

  @Test
  void should_create_use_case_when_repository_and_publisher_present() {
    contextRunner
        .withBean(OutboxRepositoryPort.class, () -> mock(OutboxRepositoryPort.class))
        .withBean(MessagePublisherPort.class, () -> mock(MessagePublisherPort.class))
        .run(context -> assertThat(context).hasSingleBean(SendOutboxMessagesPort.class));
  }

  @Test
  void should_not_create_use_case_when_publisher_missing() {
    contextRunner
        .withBean(OutboxRepositoryPort.class, () -> mock(OutboxRepositoryPort.class))
        .run(context -> assertThat(context).doesNotHaveBean(SendOutboxMessagesPort.class));
  }

  @Test
  void should_not_create_use_case_when_no_repository() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(SendOutboxMessagesPort.class));
  }

  @Test
  void should_create_nothing_when_outbox_disabled() {
    contextRunner
        .withPropertyValues("codehunters.outbox.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(EventMessageFactory.class);
              assertThat(context).doesNotHaveBean(EventMessagingProperties.class);
            });
  }

  @Test
  void should_back_off_when_custom_beans_provided() {
    var customFactory = new EventMessageFactory("custom-origin");

    contextRunner
        .withBean(EventMessageFactory.class, () -> customFactory)
        .run(
            context -> {
              assertThat(context).hasSingleBean(EventMessageFactory.class);
              assertThat(context.getBean(EventMessageFactory.class)).isSameAs(customFactory);
            });
  }

  @Test
  void should_fallback_to_application_name_when_outbox_origin_not_set() {
    contextRunner.run(
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
  void should_bind_purge_scheduler_defaults_when_nothing_configured() {
    contextRunner.run(
        context -> {
          EventMessagingProperties.Purge purge =
              context.getBean(EventMessagingProperties.class).purge();
          assertThat(purge.fixedDelay()).isEqualTo("3600000");
          assertThat(purge.initialDelay()).isEqualTo("60000");
          assertThat(purge.lockAtMost()).isEqualTo("PT10M");
          assertThat(purge.lockAtLeast()).isEqualTo("PT30S");
        });
  }

  @Test
  void should_ignore_legacy_prefix_when_bound_directly() {
    contextRunner
        .withPropertyValues("outbox.batch-size=7")
        .run(
            context ->
                assertThat(context.getBean(EventMessagingProperties.class).batchSize())
                    .isEqualTo(50));
  }

  @Test
  void should_bind_overridden_properties_when_configured() {
    contextRunner
        .withPropertyValues(
            "codehunters.outbox.publisher.type=kafka",
            "codehunters.outbox.publisher.kafka.send-timeout=3s",
            "codehunters.outbox.purge.retention=3d",
            "codehunters.outbox.purge.batch-size=100",
            "codehunters.outbox.claim.dialect=postgresql")
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
}
