package io.codehunters.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.codehunters.messaging.EventMessage;
import io.codehunters.messaging.MessagePublisherDecorator;
import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxDispatchStore;
import io.codehunters.messaging.OutboxEvent;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.SendOutboxMessagesPort;
import io.codehunters.messaging.factory.EventMessageFactory;
import io.codehunters.messaging.factory.OutboxEventFactory;
import io.codehunters.messaging.factory.TraceIdSource;
import io.codehunters.tracing.TraceContextPort;
import io.codehunters.tracing.TraceParent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EventMessagingCoreAutoConfigurationTest {

  private static final OutboxEvent<String> EVENT =
      OutboxEvent.to("t").key("a").type("n").payload("p");
  private static final String OUTBOX_CLOCK = "outboxClock";
  private static final Instant FIXED_INSTANT = Instant.parse("2026-10-09T10:00:00Z");

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
        .withBean(OutboxDispatchStore.class, () -> mock(OutboxDispatchStore.class))
        .withBean(MessagePublisherPort.class, () -> mock(MessagePublisherPort.class))
        .run(context -> assertThat(context).hasSingleBean(SendOutboxMessagesPort.class));
  }

  @Test
  void should_not_create_use_case_when_publisher_missing() {
    contextRunner
        .withBean(OutboxDispatchStore.class, () -> mock(OutboxDispatchStore.class))
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
    var customFactory =
        new EventMessageFactory("custom-origin", TraceIdSource.none(), Clock.systemUTC());

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
          EventMessage<String> event = factory.create(EVENT.traceId("trace-1"));
          assertThat(event.header().origin()).isEqualTo("test-app");
        });
  }

  @Test
  void should_bind_default_properties_when_nothing_configured() {
    contextRunner.run(
        context -> {
          EventMessagingProperties props = context.getBean(EventMessagingProperties.class);
          assertThat(props.batchSize()).isEqualTo(50);
          assertThat(props.maxAttempts()).isEqualTo(5);
          assertThat(props.claim().ttl()).isEqualTo(Duration.ofMinutes(5));
          assertThat(props.backoff().baseDelay()).isEqualTo(Duration.ofSeconds(30));
          assertThat(props.backoff().maxDelay()).isEqualTo(Duration.ofHours(1));
          assertThat(props.task().fixedDelay()).isEqualTo(Duration.ofSeconds(5));
          assertThat(props.publisher().type()).isNull();
          assertThat(props.publisher().rabbit().confirmTimeout()).isEqualTo(Duration.ofSeconds(5));
          assertThat(props.publisher().kafka().sendTimeout()).isEqualTo(Duration.ofSeconds(5));
          assertThat(props.purge().retention()).isEqualTo(Duration.ofDays(7));
          assertThat(props.purge().batchSize()).isEqualTo(500);
          assertThat(props.publisher().legacyTraceparentHeader()).isFalse();
        });
  }

  @Test
  void should_bind_legacy_traceparent_header_when_configured_on() {
    contextRunner
        .withPropertyValues("codehunters.outbox.publisher.legacy-traceparent-header=true")
        .run(
            context ->
                assertThat(
                        context
                            .getBean(EventMessagingProperties.class)
                            .publisher()
                            .legacyTraceparentHeader())
                    .isTrue());
  }

  @Test
  void should_bind_purge_scheduler_defaults_when_nothing_configured() {
    contextRunner.run(
        context -> {
          EventMessagingProperties.Purge purge =
              context.getBean(EventMessagingProperties.class).purge();
          assertThat(purge.fixedDelay()).isEqualTo(Duration.ofHours(1));
          assertThat(purge.initialDelay()).isEqualTo(Duration.ofMinutes(1));
          assertThat(purge.lockAtMost()).isEqualTo(Duration.ofMinutes(10));
          assertThat(purge.lockAtLeast()).isEqualTo(Duration.ofSeconds(30));
        });
  }

  @Test
  void should_bind_dispatch_lock_longer_than_default_dispatch_run_when_nothing_configured() {
    contextRunner.run(
        context ->
            assertThat(context.getBean(EventMessagingProperties.class).task().lockAtMost())
                .isEqualTo(Duration.ofMinutes(5)));
  }

  @Test
  void should_bind_zero_dispatch_lock_at_least_when_nothing_configured() {
    contextRunner.run(
        context ->
            assertThat(context.getBean(EventMessagingProperties.class).task().lockAtLeast())
                .isZero());
  }

  @Test
  void should_bind_duration_properties_when_configured_with_units() {
    contextRunner
        .withPropertyValues(
            "codehunters.outbox.task.fixed-delay=2s",
            "codehunters.outbox.task.lock-at-most=6m",
            "codehunters.outbox.task.lock-at-least=1s",
            "codehunters.outbox.claim.ttl=4m",
            "codehunters.outbox.backoff.base-delay=10s",
            "codehunters.outbox.backoff.max-delay=20m",
            "codehunters.outbox.purge.fixed-delay=2h",
            "codehunters.outbox.purge.initial-delay=0s",
            "codehunters.outbox.purge.lock-at-most=15m",
            "codehunters.outbox.purge.lock-at-least=1m")
        .run(
            context -> {
              EventMessagingProperties props = context.getBean(EventMessagingProperties.class);
              assertThat(props.task().fixedDelay()).isEqualTo(Duration.ofSeconds(2));
              assertThat(props.task().lockAtMost()).isEqualTo(Duration.ofMinutes(6));
              assertThat(props.task().lockAtLeast()).isEqualTo(Duration.ofSeconds(1));
              assertThat(props.claim().ttl()).isEqualTo(Duration.ofMinutes(4));
              assertThat(props.backoff().baseDelay()).isEqualTo(Duration.ofSeconds(10));
              assertThat(props.backoff().maxDelay()).isEqualTo(Duration.ofMinutes(20));
              assertThat(props.purge().fixedDelay()).isEqualTo(Duration.ofHours(2));
              assertThat(props.purge().initialDelay()).isZero();
              assertThat(props.purge().lockAtMost()).isEqualTo(Duration.ofMinutes(15));
              assertThat(props.purge().lockAtLeast()).isEqualTo(Duration.ofMinutes(1));
            });
  }

  @Test
  void should_ignore_removed_millisecond_keys_when_only_they_are_set() {
    contextRunner
        .withPropertyValues(
            "codehunters.outbox.claim.ttl-ms=1000",
            "codehunters.outbox.backoff.base-delay-ms=1",
            "codehunters.outbox.backoff.max-delay-ms=2")
        .run(
            context -> {
              EventMessagingProperties props = context.getBean(EventMessagingProperties.class);
              assertThat(props.claim().ttl()).isEqualTo(Duration.ofMinutes(5));
              assertThat(props.backoff().baseDelay()).isEqualTo(Duration.ofSeconds(30));
              assertThat(props.backoff().maxDelay()).isEqualTo(Duration.ofHours(1));
            });
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
  void should_ignore_previous_prefix_when_only_old_keys_are_set() {
    contextRunner
        .withPropertyValues("outbox.batch-size=7", "outbox.enabled=false")
        .run(
            context -> {
              assertThat(context.getBean(EventMessagingProperties.class).batchSize()).isEqualTo(50);
              assertThat(context.getBean(EventMessagingProperties.class).enabled()).isTrue();
            });
  }

  @Test
  void should_bind_overridden_properties_when_configured() {
    contextRunner
        .withPropertyValues(
            "codehunters.outbox.publisher.type=kafka",
            "codehunters.outbox.publisher.kafka.send-timeout=3s",
            "codehunters.outbox.publisher.rabbit.confirm-timeout=2s",
            "codehunters.outbox.purge.retention=3d",
            "codehunters.outbox.purge.batch-size=100")
        .run(
            context -> {
              EventMessagingProperties props = context.getBean(EventMessagingProperties.class);
              assertThat(props.publisher().type()).isEqualTo("kafka");
              assertThat(props.publisher().kafka().sendTimeout()).isEqualTo(Duration.ofSeconds(3));
              assertThat(props.publisher().rabbit().confirmTimeout())
                  .isEqualTo(Duration.ofSeconds(2));
              assertThat(props.purge().retention()).isEqualTo(Duration.ofDays(3));
              assertThat(props.purge().batchSize()).isEqualTo(100);
            });
  }

  @Test
  void should_fail_startup_explaining_duplicates_when_claim_ttl_below_kafka_publish_budget() {
    contextRunner
        .withPropertyValues(
            "codehunters.outbox.publisher.type=kafka",
            "codehunters.outbox.batch-size=100",
            "codehunters.outbox.claim.ttl=60s")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .rootCause()
                  .hasMessageContaining("codehunters.outbox.claim.ttl ")
                  .hasMessageContaining("duplicate");
            });
  }

  @Test
  void should_fail_startup_explaining_duplicates_when_claim_ttl_below_rabbit_confirm_budget() {
    contextRunner
        .withPropertyValues(
            "codehunters.outbox.publisher.type=rabbit",
            "codehunters.outbox.publisher.rabbit.confirm-timeout=10s",
            "codehunters.outbox.claim.ttl=60s")
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
        .withBean(OutboxDispatchStore.class, this::repositoryWithOneMessage)
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
        .withBean(OutboxDispatchStore.class, () -> mock(OutboxDispatchStore.class))
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
        .withBean(OutboxDispatchStore.class, () -> mock(OutboxDispatchStore.class))
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
              EventMessage<String> event = context.getBean(EventMessageFactory.class).create(EVENT);
              OutboxMessage<EventMessage<String>> outbox =
                  context.getBean(OutboxEventFactory.class).create(EVENT);
              assertThat(event.header().traceId()).isEqualTo(current.traceId());
              assertThat(outbox.message().header().traceId()).isEqualTo(current.traceId());
            });
  }

  @Test
  void should_stamp_current_traceparent_when_trace_context_port_present() {
    TraceParent current = TraceParent.generate();
    TraceContextPort port = mock(TraceContextPort.class);
    when(port.currentTraceParent()).thenReturn(Optional.of(current));

    contextRunner
        .withBean(TraceContextPort.class, () -> port)
        .run(
            context ->
                assertThat(context.getBean(OutboxEventFactory.class).create(EVENT).traceparent())
                    .isEqualTo(current.format()));
  }

  @Test
  void should_restore_stored_trace_context_around_publishes_when_tracer_present() {
    contextRunner
        .withBean(Tracer.class, () -> Tracer.NOOP)
        .run(context -> assertThat(context).hasBean("outboxTraceContextDecorator"));
  }

  @Test
  void should_not_restore_trace_context_when_no_tracer_bean() {
    contextRunner.run(
        context -> assertThat(context).doesNotHaveBean("outboxTraceContextDecorator"));
  }

  @Test
  void should_create_use_case_when_micrometer_tracing_absent() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(Tracer.class))
        .withBean(OutboxDispatchStore.class, () -> mock(OutboxDispatchStore.class))
        .withBean(MessagePublisherPort.class, () -> mock(MessagePublisherPort.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(SendOutboxMessagesPort.class);
              assertThat(context).doesNotHaveBean("outboxTraceContextDecorator");
            });
  }

  @Test
  void should_leave_trace_id_empty_when_tracing_absent() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(TraceContextPort.class))
        .run(
            context ->
                assertThat(
                        context.getBean(EventMessageFactory.class).create(EVENT).header().traceId())
                    .isNull());
  }

  @Test
  void should_provide_private_utc_outbox_clock_when_no_clock_defined() {
    contextRunner.run(
        context -> {
          assertThat(context.getBean(OUTBOX_CLOCK, Clock.class).getZone())
              .isEqualTo(ZoneOffset.UTC);
          assertThat(definition(context, OUTBOX_CLOCK).isDefaultCandidate()).isFalse();
        });
  }

  @Test
  void should_keep_application_clock_resolvable_by_type_when_clock_bean_defined() {
    Clock custom = Clock.fixed(Instant.EPOCH, ZoneId.of("Europe/Madrid"));

    contextRunner
        .withBean(Clock.class, () -> custom)
        .run(
            context -> {
              assertThat(context.getBean(Clock.class)).isSameAs(custom);
              assertThat(context.getBean(OUTBOX_CLOCK)).isNotSameAs(custom);
            });
  }

  @Test
  void should_not_stamp_events_with_application_clock_when_clock_bean_defined() {
    Clock custom = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    contextRunner
        .withBean(Clock.class, () -> custom)
        .run(
            context ->
                assertThat(
                        context
                            .getBean(EventMessageFactory.class)
                            .create(EVENT)
                            .header()
                            .timestamp())
                    .isAfter(Instant.EPOCH));
  }

  @Test
  void should_stamp_events_with_outbox_clock_when_application_overrides_it_by_name() {
    Clock outboxClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    contextRunner
        .withBean(OUTBOX_CLOCK, Clock.class, () -> outboxClock)
        .run(
            context ->
                assertThat(
                        context
                            .getBean(EventMessageFactory.class)
                            .create(EVENT)
                            .header()
                            .timestamp())
                    .isEqualTo(FIXED_INSTANT));
  }

  private static AbstractBeanDefinition definition(
      AssertableApplicationContext context, String name) {
    return (AbstractBeanDefinition) context.getBeanFactory().getBeanDefinition(name);
  }

  private OutboxDispatchStore repositoryWithOneMessage() {
    OutboxDispatchStore repository = mock(OutboxDispatchStore.class);
    OutboxMessage<String> message =
        OutboxMessage.<String>builder().topic("events").key("k").message("{}").build();
    when(repository.claim(any())).thenReturn(List.of(message));
    when(repository.markSendStarted(message)).thenReturn(Optional.of(message));
    return repository;
  }
}
