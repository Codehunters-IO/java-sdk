package io.codehunters.messaging.jdbc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxRepositoryPort;
import io.codehunters.messaging.SendOutboxMessagesPort;
import io.codehunters.messaging.config.EventMessagingCoreAutoConfiguration;
import io.codehunters.messaging.jdbc.metrics.OutboxFailedGauge;
import io.codehunters.messaging.jdbc.scheduler.OutboxPurgeScheduler;
import io.codehunters.messaging.jdbc.scheduler.OutboxScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import tools.jackson.databind.json.JsonMapper;

class EventMessagingJdbcAutoConfigurationTest {

  private static final String SCHEDULED_ANNOTATION_PROCESSOR =
      TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME;

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  EventMessagingJdbcAutoConfiguration.class,
                  EventMessagingCoreAutoConfiguration.class,
                  OutboxDispatchSchedulerAutoConfiguration.class))
          .withPropertyValues("spring.application.name=test-app");

  @Test
  void should_create_store_beans_when_datasource_available() {
    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .run(
            context -> {
              assertThat(context).hasSingleBean(JsonMapper.class);
              assertThat(context).hasSingleBean(OutboxRepositoryPort.class);
              assertThat(context).hasSingleBean(LockProvider.class);
              assertThat(context).hasSingleBean(OutboxPurgeScheduler.class);
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
              assertThat(context).doesNotHaveBean(SCHEDULED_ANNOTATION_PROCESSOR);
            });
  }

  @Test
  void should_enable_scheduling_when_scheduling_property_unset() {
    contextRunner
        .withBean(DataSource.class, this::createDataSource)
        .run(context -> assertThat(context).hasBean(SCHEDULED_ANNOTATION_PROCESSOR));
  }

  @Test
  void should_not_create_message_publisher_when_no_transport_present() {
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
  void should_not_create_outbox_beans_when_no_datasource() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(OutboxRepositoryPort.class);
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
              assertThat(context).hasSingleBean(OutboxRepositoryPort.class);
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
              assertThat(context).hasSingleBean(OutboxRepositoryPort.class);
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
              assertThat(context).hasSingleBean(OutboxRepositoryPort.class);
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
        .withBean(DataSource.class, () -> reporting("H2"))
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("H2"));
  }

  // A mock that cannot connect: the PostgreSQL check skips it, so wiring runs without a database.
  private DataSource createDataSource() {
    return mock(DataSource.class);
  }

  private static DataSource reporting(String product) {
    try {
      DataSource dataSource = mock(DataSource.class);
      Connection connection = mock(Connection.class);
      DatabaseMetaData metaData = mock(DatabaseMetaData.class);
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData()).thenReturn(metaData);
      when(metaData.getDatabaseProductName()).thenReturn(product);
      return dataSource;
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }
}
