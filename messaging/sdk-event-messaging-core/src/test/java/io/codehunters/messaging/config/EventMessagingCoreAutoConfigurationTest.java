package io.codehunters.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.codehunters.messaging.EventMessage;
import io.codehunters.messaging.MessagePublisherDecorator;
import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.OutboxRepositoryPort;
import io.codehunters.messaging.SendOutboxMessagesPort;
import io.codehunters.messaging.factory.EventMessageFactory;
import io.codehunters.messaging.factory.OutboxEventFactory;
import io.codehunters.tracing.TraceContextPort;
import io.codehunters.tracing.TraceParent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
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
          assertThat(props.publisher().type()).isNull();
          assertThat(props.publisher().rabbit().confirmTimeout()).isEqualTo(Duration.ofSeconds(5));
          assertThat(props.publisher().kafka().sendTimeout()).isEqualTo(Duration.ofSeconds(5));
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
  void should_bind_dispatch_lock_longer_than_default_dispatch_run_when_nothing_configured() {
    contextRunner.run(
        context ->
            assertThat(context.getBean(EventMessagingProperties.class).task().lockAtMost())
                .isEqualTo("300000"));
  }

  @Test
  void should_fail_startup_naming_lock_key_when_lock_at_most_shorter_than_dispatch_run() {
    contextRunner
        .withPropertyValues(
            "codehunters.outbox.publisher.type=kafka", "codehunters.outbox.task.lock-at-most=60s")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("codehunters.outbox.task.lock-at-most"));
  }

  @Test
  void should_bind_scheduling_enabled_when_nothing_configured() {
    contextRunner.run(
        context ->
            assertThat(context.getBean(EventMessagingProperties.class).scheduling().enabled())
                .isTrue());
  }

  @Test
  void should_bind_scheduling_disabled_when_configured_off() {
    contextRunner
        .withPropertyValues("codehunters.outbox.scheduling.enabled=false")
        .run(
            context ->
                assertThat(context.getBean(EventMessagingProperties.class).scheduling().enabled())
                    .isFalse());
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
            "codehunters.outbox.publisher.rabbit.confirm-timeout=2s",
            "codehunters.outbox.purge.retention=3d",
            "codehunters.outbox.purge.batch-size=100",
            "codehunters.outbox.claim.dialect=postgresql")
        .run(
            context -> {
              EventMessagingProperties props = context.getBean(EventMessagingProperties.class);
              assertThat(props.publisher().type()).isEqualTo("kafka");
              assertThat(props.publisher().kafka().sendTimeout()).isEqualTo(Duration.ofSeconds(3));
              assertThat(props.publisher().rabbit().confirmTimeout())
                  .isEqualTo(Duration.ofSeconds(2));
              assertThat(props.purge().retention()).isEqualTo(Duration.ofDays(3));
              assertThat(props.purge().batchSize()).isEqualTo(100);
              assertThat(props.claim().dialect()).isEqualTo("postgresql");
            });
  }

  @Test
  void should_fail_startup_explaining_duplicates_when_claim_ttl_below_kafka_publish_budget() {
    contextRunner
        .withPropertyValues(
            "codehunters.outbox.publisher.type=kafka",
            "codehunters.outbox.batch-size=100",
            "codehunters.outbox.claim.ttl-ms=60000")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .rootCause()
                  .hasMessageContaining("codehunters.outbox.claim.ttl-ms")
                  .hasMessageContaining("duplicate");
            });
  }

  @Test
  void should_fail_startup_explaining_duplicates_when_claim_ttl_below_rabbit_confirm_budget() {
    contextRunner
        .withPropertyValues(
            "codehunters.outbox.publisher.type=rabbit",
            "codehunters.outbox.publisher.rabbit.confirm-timeout=10s",
            "codehunters.outbox.claim.ttl-ms=60000")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("codehunters.outbox.publisher.rabbit.confirm-timeout")
                    .hasMessageContaining("duplicate"));
  }

  @Test
  void should_fail_startup_naming_key_when_publisher_type_unknown() {
    contextRunner
        .withPropertyValues("codehunters.outbox.publisher.type=sqs")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("codehunters.outbox.publisher.type"));
  }

  @Test
  void should_time_publishes_when_meter_registry_present() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();

    contextRunner
        .withBean(OutboxRepositoryPort.class, this::repositoryWithOneMessage)
        .withBean(MessagePublisherPort.class, () -> mock(MessagePublisherPort.class))
        .withBean(MeterRegistry.class, () -> registry)
        .run(
            context -> {
              context.getBean(SendOutboxMessagesPort.class).execute();
              assertThat(registry.get("outbox.publish").tag("outcome", "success").timer().count())
                  .isEqualTo(1);
            });
  }

  @Test
  void should_not_decorate_publisher_when_no_meter_registry() {
    contextRunner
        .withBean(OutboxRepositoryPort.class, () -> mock(OutboxRepositoryPort.class))
        .withBean(MessagePublisherPort.class, () -> mock(MessagePublisherPort.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(SendOutboxMessagesPort.class);
              assertThat(context).doesNotHaveBean(MessagePublisherDecorator.class);
            });
  }

  @Test
  void should_create_use_case_when_micrometer_absent() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
        .withBean(OutboxRepositoryPort.class, () -> mock(OutboxRepositoryPort.class))
        .withBean(MessagePublisherPort.class, () -> mock(MessagePublisherPort.class))
        .run(context -> assertThat(context).hasSingleBean(SendOutboxMessagesPort.class));
  }

  @Test
  void should_stamp_current_trace_id_when_trace_context_port_present() {
    TraceParent current = TraceParent.generate();
    TraceContextPort port = mock(TraceContextPort.class);
    when(port.currentTraceParent()).thenReturn(Optional.of(current));

    contextRunner
        .withBean(TraceContextPort.class, () -> port)
        .run(
            context -> {
              EventMessage<String> event =
                  context.getBean(EventMessageFactory.class).create(null, "a", "n", "T", "p");
              OutboxMessage<EventMessage<String>> outbox =
                  context.getBean(OutboxEventFactory.class).create("t", null, "a", "n", "T", "p");
              assertThat(event.getHeader().getTraceId()).isEqualTo(current.traceId());
              assertThat(outbox.getMessage().getHeader().getTraceId()).isEqualTo(current.traceId());
            });
  }

  @Test
  void should_leave_trace_id_empty_when_tracing_absent() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(TraceContextPort.class))
        .run(
            context ->
                assertThat(
                        context
                            .getBean(EventMessageFactory.class)
                            .create(null, "a", "n", "T", "p")
                            .getHeader()
                            .getTraceId())
                    .isNull());
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

  private OutboxRepositoryPort repositoryWithOneMessage() {
    OutboxRepositoryPort repository = mock(OutboxRepositoryPort.class);
    OutboxMessage<String> message =
        OutboxMessage.<String>builder().topic("events").key("k").message("{}").build();
    when(repository.claimPendingMessages(any(), anyInt(), any(), any()))
        .thenReturn(List.of(message));
    when(repository.markSendStarted(message)).thenReturn(true);
    return repository;
  }
}
