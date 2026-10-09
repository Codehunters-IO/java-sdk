package io.codehunters.messaging.jdbc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxDispatchStore;
import io.codehunters.messaging.OutboxWriter;
import io.codehunters.messaging.SendOutboxMessagesPort;
import io.codehunters.messaging.config.EventMessagingCoreAutoConfiguration;
import io.codehunters.messaging.jdbc.OutboxJdbcAdapter;
import io.codehunters.messaging.jdbc.metrics.OutboxFailedGauge;
import io.codehunters.messaging.jdbc.scheduler.OutboxPurgeScheduler;
import io.codehunters.messaging.jdbc.scheduler.OutboxScheduler;
import io.codehunters.messaging.jdbc.scheduler.OutboxSchedulingLifecycle;
import io.codehunters.messaging.jdbc.scheduler.RecordingLockProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import tools.jackson.databind.json.JsonMapper;

class EventMessagingJdbcAutoConfigurationTest {

  private static final String SCHEDULED_ANNOTATION_PROCESSOR =
      TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME;
  private static final String OUTBOX_TASK_SCHEDULER = "outboxTaskScheduler";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  EventMessagingJdbcAutoConfiguration.class,
                  EventMessagingCoreAutoConfiguration.class,
                  OutboxDispatchSchedulerAutoConfiguration.class,
                  OutboxMetricsAutoConfiguration.class))
          .withPropertyValues("spring.application.name=test-app");

  @Test
  void should_create_store_beans_when_datasource_available() {
    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .run(
            context -> {
              assertThat(context).hasSingleBean(JsonMapper.class);
              assertThat(context).hasSingleBean(OutboxDispatchStore.class);
              assertThat(context).hasSingleBean(LockProvider.class);
              assertThat(context).hasSingleBean(OutboxPurgeScheduler.class);
            });
  }

  @Test
  void should_expose_one_adapter_as_writer_and_dispatch_store_when_datasource_available() {
    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .run(
            context -> {
              assertThat(context).hasSingleBean(OutboxWriter.class);
              assertThat(context.getBean(OutboxWriter.class))
                  .isSameAs(context.getBean(OutboxDispatchStore.class))
                  .isSameAs(context.getBean("outboxJdbcAdapter"));
            });
  }

  @Test
  void should_back_off_when_application_defines_an_outbox_writer() {
    OutboxWriter applicationWriter = mock(OutboxWriter.class);

    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .withBean(OutboxWriter.class, () -> applicationWriter)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(OutboxJdbcAdapter.class);
              assertThat(context).doesNotHaveBean(OutboxFailedGauge.class);
              assertThat(context.getBean(OutboxWriter.class)).isSameAs(applicationWriter);
            });
  }

  @Test
  void should_back_off_when_application_defines_an_outbox_dispatch_store() {
    OutboxDispatchStore applicationStore = mock(OutboxDispatchStore.class);

    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .withBean(OutboxDispatchStore.class, () -> applicationStore)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(OutboxJdbcAdapter.class);
              assertThat(context).hasSingleBean(OutboxFailedGauge.class);
              assertThat(context.getBean(OutboxDispatchStore.class)).isSameAs(applicationStore);
            });
  }

  @Test
  void should_create_use_case_and_dispatch_scheduler_when_publisher_present() {
    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .withBean(MessagePublisherPort.class, () -> mock(MessagePublisherPort.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(SendOutboxMessagesPort.class);
              assertThat(context).hasSingleBean(OutboxScheduler.class);
            });
  }

  @Test
  void should_keep_use_case_without_schedulers_when_scheduling_disabled() {
    contextRunner
        .withPropertyValues("codehunters.outbox.scheduling.enabled=false")
        .withBean(DataSource.class, this::createDataSource)
        .withBean(MessagePublisherPort.class, () -> mock(MessagePublisherPort.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(SendOutboxMessagesPort.class);
              assertThat(context).doesNotHaveBean(OutboxScheduler.class);
              assertThat(context).doesNotHaveBean(OutboxPurgeScheduler.class);
              assertThat(context).doesNotHaveBean(OUTBOX_TASK_SCHEDULER);
              assertThat(context).doesNotHaveBean(OutboxSchedulingLifecycle.class);
              assertThat(context).doesNotHaveBean(SCHEDULED_ANNOTATION_PROCESSOR);
            });
  }

  @Test
  void should_not_enable_application_wide_scheduling_when_scheduling_property_unset() {
    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .withBean(MessagePublisherPort.class, () -> mock(MessagePublisherPort.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(OutboxScheduler.class);
              assertThat(context).doesNotHaveBean(SCHEDULED_ANNOTATION_PROCESSOR);
            });
  }

  @Test
  void should_register_private_outbox_task_scheduler_when_scheduling_property_unset() {
    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .run(
            context -> {
              assertThat(context).hasBean(OUTBOX_TASK_SCHEDULER);
              assertThat(context).hasSingleBean(OutboxSchedulingLifecycle.class);
              assertThat(definition(context, OUTBOX_TASK_SCHEDULER).isDefaultCandidate()).isFalse();
            });
  }

  @Test
  void should_resolve_application_task_scheduler_by_type_when_outbox_scheduler_present() {
    ThreadPoolTaskScheduler applicationScheduler = new ThreadPoolTaskScheduler();

    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .withBean("taskScheduler", TaskScheduler.class, () -> applicationScheduler)
        .run(
            context -> {
              assertThat(context.getBean(TaskScheduler.class)).isSameAs(applicationScheduler);
              assertThat(context.getBean(OUTBOX_TASK_SCHEDULER)).isNotSameAs(applicationScheduler);
            });
  }

  @Test
  void should_run_dispatch_and_purge_under_their_locks_when_context_starts() {
    RecordingLockProvider locks = RecordingLockProvider.granting(2);

    contextRunner
        .withPropertyValues("codehunters.outbox.purge.initial-delay=0s")
        .withBean(OutboxDispatchStore.class, () -> mock(OutboxDispatchStore.class))
        .withBean(SendOutboxMessagesPort.class, () -> mock(SendOutboxMessagesPort.class))
        .withBean(LockProvider.class, () -> locks)
        .run(
            context -> {
              assertThat(locks.awaitRequests()).isTrue();
              assertThat(locks.requests())
                  .extracting(LockConfiguration::getName)
                  .contains("outbox-test-app", "outbox-purge-test-app");
            });
  }

  @Test
  void should_hold_dispatch_lock_for_configured_bounds_when_dispatch_runs() {
    RecordingLockProvider locks = RecordingLockProvider.granting();

    contextRunner
        .withPropertyValues(
            "codehunters.outbox.task.lock-at-most=7m", "codehunters.outbox.task.lock-name=d")
        .withBean(OutboxDispatchStore.class, () -> mock(OutboxDispatchStore.class))
        .withBean(SendOutboxMessagesPort.class, () -> mock(SendOutboxMessagesPort.class))
        .withBean(LockProvider.class, () -> locks)
        .run(
            context -> {
              assertThat(locks.awaitRequests()).isTrue();
              assertThat(locks.requests().getFirst())
                  .extracting(
                      LockConfiguration::getName,
                      LockConfiguration::getLockAtMostFor,
                      LockConfiguration::getLockAtLeastFor)
                  .containsExactly("d", Duration.ofMinutes(7), Duration.ZERO);
            });
  }

  @Test
  void should_not_create_message_publisher_when_no_transport_present() {
    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .run(
            context -> {
              assertThat(context).hasSingleBean(OutboxDispatchStore.class);
              assertThat(context).doesNotHaveBean(MessagePublisherPort.class);
              assertThat(context).doesNotHaveBean(SendOutboxMessagesPort.class);
              assertThat(context).doesNotHaveBean(OutboxScheduler.class);
            });
  }

  @Test
  void should_not_create_outbox_beans_when_no_datasource() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(OutboxDispatchStore.class);
          assertThat(context).doesNotHaveBean(SendOutboxMessagesPort.class);
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
              assertThat(context).hasSingleBean(OutboxDispatchStore.class);
              assertThat(context).hasSingleBean(LockProvider.class);
              assertThat(context).hasSingleBean(OutboxPurgeScheduler.class);
              assertThat(context).doesNotHaveBean(MessagePublisherPort.class);
            });
  }

  @Test
  void should_keep_boot_json_mapper_primary_when_jackson_auto_configuration_present() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration.class,
                EventMessagingJdbcAutoConfiguration.class,
                EventMessagingCoreAutoConfiguration.class))
        .withBean(DataSource.class, this::createDataSource)
        .run(
            context -> {
              assertThat(context).hasBean("jacksonJsonMapper");
              assertThat(context).hasBean("outboxJsonMapper");
              assertThat(
                      context.getBeanFactory().getBeanDefinition("jacksonJsonMapper").isPrimary())
                  .isTrue();
              assertThat(context).hasSingleBean(OutboxDispatchStore.class);
            });
  }

  @Test
  void should_resolve_boot_json_mapper_by_type_when_jackson_auto_configuration_present() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                JacksonAutoConfiguration.class,
                EventMessagingJdbcAutoConfiguration.class,
                EventMessagingCoreAutoConfiguration.class))
        .withBean(DataSource.class, this::createDataSource)
        .run(
            context -> {
              assertThat(context.getBean(JsonMapper.class))
                  .isSameAs(context.getBean("jacksonJsonMapper"));
              AbstractBeanDefinition outboxMapper =
                  (AbstractBeanDefinition)
                      context.getBeanFactory().getBeanDefinition("outboxJsonMapper");
              assertThat(outboxMapper.isDefaultCandidate()).isFalse();
            });
  }

  @Test
  void should_resolve_application_json_mapper_by_type_when_it_is_not_primary() {
    JsonMapper applicationMapper = JsonMapper.builder().build();

    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .withBean("applicationJsonMapper", JsonMapper.class, () -> applicationMapper)
        .run(
            context -> {
              assertThat(context.getBean(JsonMapper.class)).isSameAs(applicationMapper);
              assertThat(context).hasSingleBean(OutboxDispatchStore.class);
            });
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

  @Test
  void should_fail_startup_when_datasource_is_not_postgresql() {
    contextRunner
        .withBean(DataSource.class, () -> DataSources.reporting("H2"))
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("H2"));
  }

  @Test
  void should_start_when_database_is_unreachable_at_startup() {
    contextRunner
        .withBean(DataSource.class, DataSources::unreachable)
        .run(context -> assertThat(context).hasNotFailed());
  }

  private static AbstractBeanDefinition definition(
      AssertableApplicationContext context, String name) {
    return (AbstractBeanDefinition) context.getBeanFactory().getBeanDefinition(name);
  }

  private DataSource createDataSource() {
    return DataSources.postgres();
  }
}
