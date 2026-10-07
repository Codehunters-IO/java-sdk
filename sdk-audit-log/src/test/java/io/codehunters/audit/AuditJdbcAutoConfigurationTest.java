package io.codehunters.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import tools.jackson.databind.json.JsonMapper;

class AuditJdbcAutoConfigurationTest {

  private static final AuditEvent EVENT =
      new AuditEvent(
          "ORDER",
          "ORD-1",
          "CREATE",
          "user-1",
          "USER",
          null,
          Map.of(),
          "SUCCESS",
          null,
          "order-service",
          OffsetDateTime.parse("2026-10-06T12:00:00Z"));

  private static final String[] SINGLE_WORKER = {
    "codehunters.audit.async.core-pool-size=1", "codehunters.audit.async.max-pool-size=1"
  };

  private final DataSource dataSource = auditDatabase();

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  AopAutoConfiguration.class,
                  AuditJdbcAutoConfiguration.class,
                  AuditAutoConfiguration.class))
          .withBean("dataSource", DataSource.class, () -> dataSource);

  @Test
  void should_publish_asynchronously_by_default() {
    runner.withPropertyValues(SINGLE_WORKER).run(this::assertWrittenOnlyByTheWorker);
  }

  @Test
  void should_publish_synchronously_when_fail_on_error_is_set() {
    runner
        .withPropertyValues("codehunters.audit.fail-on-error=true")
        .run(
            ctx -> {
              ctx.getBean(AuditEventPublisherPort.class).publish(EVENT);
              assertThat(scheduledTasks(ctx)).isZero();
              assertThat(countRows()).isEqualTo(1);
            });
  }

  @Test
  void should_publish_synchronously_when_async_is_disabled() {
    runner
        .withPropertyValues("codehunters.audit.async.enabled=false")
        .run(
            ctx -> {
              ctx.getBean(AuditEventPublisherPort.class).publish(EVENT);
              assertThat(scheduledTasks(ctx)).isZero();
              assertThat(countRows()).isEqualTo(1);
            });
  }

  @Test
  void should_create_audit_aspect_when_jdbc_publisher_exists() {
    runner.run(ctx -> assertThat(ctx).hasSingleBean(AuditAspect.class));
  }

  @Test
  void should_back_off_when_custom_publisher_provided() {
    runner
        .withBean(AuditEventPublisherPort.class, () -> event -> {})
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(AuditEventPublisherPort.class);
              assertThat(ctx).doesNotHaveBean("auditEventPublisher");
            });
  }

  @Test
  void should_not_create_jdbc_publisher_when_no_datasource() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                AopAutoConfiguration.class,
                AuditJdbcAutoConfiguration.class,
                AuditAutoConfiguration.class))
        .run(ctx -> assertThat(ctx).doesNotHaveBean(AuditEventPublisherPort.class));
  }

  @Test
  void should_not_create_jdbc_publisher_when_jdbc_client_is_missing() {
    runner
        .withClassLoader(new FilteredClassLoader("org.springframework.jdbc"))
        .run(ctx -> assertThat(ctx).doesNotHaveBean(AuditEventPublisherPort.class));
  }

  @Test
  void should_keep_audit_json_mapper_out_of_application_injection() {
    runner
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
        .run(
            ctx -> {
              assertThat(ctx).hasBean("auditJsonMapper");
              assertThat(ctx.getBean(JsonMapper.class)).isNotSameAs(ctx.getBean("auditJsonMapper"));
            });
  }

  @Test
  void should_keep_application_task_executor() {
    runner
        .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
        .run(
            ctx -> {
              assertThat(ctx).hasBean("auditTaskExecutor");
              assertThat(ctx).hasBean("applicationTaskExecutor");
            });
  }

  @Test
  void should_not_enable_async_processing_for_the_application() {
    runner.run(
        ctx ->
            assertThat(ctx)
                .doesNotHaveBean(TaskManagementConfigUtils.ASYNC_ANNOTATION_PROCESSOR_BEAN_NAME));
  }

  @Test
  void should_configure_executor_from_properties() {
    runner
        .withPropertyValues(
            "codehunters.audit.async.core-pool-size=3",
            "codehunters.audit.async.max-pool-size=5",
            "codehunters.audit.async.queue-capacity=7")
        .run(
            ctx -> {
              var executor = ctx.getBean("auditTaskExecutor", ThreadPoolTaskExecutor.class);
              assertThat(executor.getCorePoolSize()).isEqualTo(3);
              assertThat(executor.getMaxPoolSize()).isEqualTo(5);
              assertThat(executor.getQueueCapacity()).isEqualTo(7);
              assertThat(executor.getThreadNamePrefix()).isEqualTo("audit-");
            });
  }

  @Test
  void should_drain_executor_before_closing_the_data_source() {
    runner.run(
        ctx ->
            assertThat(ctx.getBeanFactory().getDependenciesForBean("auditTaskExecutor"))
                .contains("dataSource"));
  }

  @Test
  void should_write_queued_events_before_the_context_closes() {
    runner.run(ctx -> ctx.getBean(AuditEventPublisherPort.class).publish(EVENT));

    assertThat(countRows()).isEqualTo(1);
  }

  @Test
  void should_fail_audited_call_when_fail_on_error_and_insert_fails() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                AopAutoConfiguration.class,
                AuditJdbcAutoConfiguration.class,
                AuditAutoConfiguration.class))
        .withBean("dataSource", DataSource.class, AuditJdbcAutoConfigurationTest::emptyDatabase)
        .withBean(OrderService.class)
        .withPropertyValues("codehunters.audit.fail-on-error=true")
        .run(
            ctx ->
                assertThatThrownBy(() -> ctx.getBean(OrderService.class).findOrder("ORD-1"))
                    .isInstanceOf(AuditPersistenceException.class));
  }

  @Test
  void should_count_rejected_events_when_meter_registry_present() {
    runner
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .run(
            ctx -> {
              ctx.getBean(AuditEventRejectionListener.class).rejected(EVENT);
              assertThat(ctx.getBean(MeterRegistry.class).counter("audit.events.rejected").count())
                  .isEqualTo(1.0);
            });
  }

  @Test
  void should_publish_asynchronously_without_micrometer() {
    runner
        .withClassLoader(new FilteredClassLoader("io.micrometer"))
        .withPropertyValues(SINGLE_WORKER)
        .run(this::assertWrittenOnlyByTheWorker);
  }

  @Test
  void should_not_configure_jdbc_publisher_when_data_source_is_ambiguous() {
    runner
        .withBean(
            "secondDataSource", DataSource.class, AuditJdbcAutoConfigurationTest::emptyDatabase)
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx).doesNotHaveBean(AuditEventPublisherPort.class);
            });
  }

  @Test
  void should_register_audit_counters_before_any_event_is_lost() {
    runner
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .run(
            ctx -> {
              assertThat(counter(ctx, "audit.events.rejected")).isZero();
              assertThat(counter(ctx, "audit.events.failed")).isZero();
            });
  }

  @Test
  void should_count_events_that_could_not_be_written() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                AopAutoConfiguration.class,
                AuditJdbcAutoConfiguration.class,
                AuditAutoConfiguration.class))
        .withBean("dataSource", DataSource.class, AuditJdbcAutoConfigurationTest::emptyDatabase)
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .withPropertyValues("codehunters.audit.async.enabled=false")
        .run(
            ctx -> {
              ctx.getBean(AuditEventPublisherPort.class).publish(EVENT);
              assertThat(counter(ctx, "audit.events.failed")).isEqualTo(1.0);
            });
  }

  @Test
  void should_count_events_the_saturated_executor_rejects() {
    runner
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .withPropertyValues(
            "codehunters.audit.async.core-pool-size=1",
            "codehunters.audit.async.max-pool-size=1",
            "codehunters.audit.async.queue-capacity=0")
        .run(
            ctx -> {
              var worker = new CountDownLatch(1);
              ctx.getBean("auditTaskExecutor", ThreadPoolTaskExecutor.class)
                  .execute(() -> awaitQuietly(worker));
              ctx.getBean(AuditEventPublisherPort.class).publish(EVENT);
              worker.countDown();
              assertThat(counter(ctx, "audit.events.rejected")).isEqualTo(1.0);
            });
  }

  @Test
  void should_order_the_aspect_from_properties() {
    runner
        .withPropertyValues("codehunters.audit.order=5")
        .run(ctx -> assertThat(ctx.getBean(AuditAspect.class).getOrder()).isEqualTo(5));
  }

  @Test
  void should_give_the_aspect_the_lowest_precedence_by_default() {
    runner.run(
        ctx ->
            assertThat(ctx.getBean(AuditAspect.class).getOrder())
                .isEqualTo(Ordered.LOWEST_PRECEDENCE));
  }

  @Test
  void should_fail_audited_call_when_fail_on_error_and_event_cannot_be_built() {
    runner
        .withBean(Clock.class, BrokenClock::new)
        .withBean(OrderService.class)
        .withPropertyValues("codehunters.audit.fail-on-error=true")
        .run(
            ctx ->
                assertThatThrownBy(() -> ctx.getBean(OrderService.class).findOrder("ORD-1"))
                    .isInstanceOf(AuditPersistenceException.class));
  }

  private static double counter(ApplicationContext ctx, String name) {
    return ctx.getBean(MeterRegistry.class).get(name).counter().count();
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Holds the only audit worker on a latch while the event is published: the row cannot exist until
   * the worker is released, so a synchronous write fails the first assertion deterministically, and
   * the asynchronous one is awaited instead of sampled.
   */
  private void assertWrittenOnlyByTheWorker(ApplicationContext ctx) {
    var worker = new CountDownLatch(1);
    ctx.getBean("auditTaskExecutor", ThreadPoolTaskExecutor.class)
        .execute(() -> awaitQuietly(worker));
    ctx.getBean(AuditEventPublisherPort.class).publish(EVENT);
    assertThat(countRows()).isZero();
    worker.countDown();
    await().atMost(Duration.ofSeconds(5)).until(() -> countRows() == 1);
  }

  private static long scheduledTasks(ApplicationContext ctx) {
    return ctx.getBean("auditTaskExecutor", ThreadPoolTaskExecutor.class)
        .getThreadPoolExecutor()
        .getTaskCount();
  }

  private int countRows() {
    return JdbcClient.create(dataSource)
        .sql("SELECT COUNT(*) FROM audit_event")
        .query(Integer.class)
        .single();
  }

  private static DataSource auditDatabase() {
    DataSource database = emptyDatabase();
    new ResourceDatabasePopulator(new ClassPathResource("db/test-schema.sql")).execute(database);
    return database;
  }

  private static DataSource emptyDatabase() {
    var database = new DriverManagerDataSource();
    database.setUrl(
        "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
    return database;
  }
}
