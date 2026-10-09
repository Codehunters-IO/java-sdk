package io.codehunters.audit.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.audit.Auditable;
import io.codehunters.audit.internal.aspect.InMemoryAuditEventPublisher;
import io.codehunters.audit.internal.aspect.OrderService;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Without an {@link io.codehunters.audit.AuditEventPublisherPort} the aspect is never registered,
 * so every {@code @Auditable} call would go unrecorded without a word. Startup reports why, and
 * fails only when {@code codehunters.audit.startup-check.fail-fast} is set.
 */
@ExtendWith(OutputCaptureExtension.class)
class AuditPublisherStartupCheckTest {

  private static final String INACTIVE = "no AuditEventPublisherPort";
  private static final String AMBIGUOUS = "2 DataSource beans and none is marked @Primary";
  private static final String NO_DATA_SOURCE = "no DataSource bean";
  private static final String NOT_CONFIGURED = "missing classes or auto-configuration excluded";
  private static final String NO_WEAVER = "aspectjweaver";
  private static final String FAIL_FAST = "codehunters.audit.startup-check.fail-fast=true";
  private static final String FAIL_ON_ERROR = "codehunters.audit.fail-on-error=true";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  AopAutoConfiguration.class,
                  AuditJdbcAutoConfiguration.class,
                  AuditAutoConfiguration.class));

  private final ApplicationContextRunner audited =
      runner.withBean("orderService", OrderService.class, OrderService::new);

  private final ApplicationContextRunner twoDataSources =
      audited
          .withBean("firstDataSource", DataSource.class, AuditPublisherStartupCheckTest::database)
          .withBean("secondDataSource", DataSource.class, AuditPublisherStartupCheckTest::database);

  @Test
  void should_log_an_error_naming_the_ambiguous_data_source_when_auditing_is_inactive(
      CapturedOutput output) {
    twoDataSources.run(context -> assertThat(context).hasNotFailed());

    assertThat(output).contains("ERROR", INACTIVE, AMBIGUOUS, OrderService.class.getName());
  }

  @Test
  void should_fail_startup_when_the_data_source_is_ambiguous_and_fail_fast_is_set() {
    twoDataSources
        .withPropertyValues(FAIL_FAST)
        .run(context -> assertThat(context).getFailure().hasMessageContaining(AMBIGUOUS));
  }

  @Test
  void should_only_log_when_fail_on_error_is_set_without_fail_fast(CapturedOutput output) {
    twoDataSources
        .withPropertyValues(FAIL_ON_ERROR)
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(output).contains("ERROR", AMBIGUOUS);
  }

  @Test
  void should_log_an_error_naming_the_missing_data_source_when_auditing_is_inactive(
      CapturedOutput output) {
    audited.run(context -> assertThat(context).hasNotFailed());

    assertThat(output).contains(INACTIVE, NO_DATA_SOURCE);
  }

  @Test
  void should_fail_startup_without_a_data_source_when_fail_fast_is_set() {
    audited
        .withPropertyValues(FAIL_FAST)
        .run(context -> assertThat(context).getFailure().hasMessageContaining(NO_DATA_SOURCE));
  }

  @Test
  void should_name_the_unconfigured_jdbc_publisher_when_the_data_source_is_unique(
      CapturedOutput output) {
    audited
        .withClassLoader(new FilteredClassLoader("org.springframework.jdbc"))
        .withBean("dataSource", DataSource.class, AuditPublisherStartupCheckTest::database)
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(output).contains(INACTIVE, NOT_CONFIGURED);
  }

  @Test
  void should_name_the_unconfigured_jdbc_publisher_when_its_auto_configuration_is_excluded(
      CapturedOutput output) {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(AopAutoConfiguration.class, AuditAutoConfiguration.class))
        .withBean("orderService", OrderService.class, OrderService::new)
        .withBean("dataSource", DataSource.class, AuditPublisherStartupCheckTest::database)
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(output).contains(INACTIVE, NOT_CONFIGURED);
  }

  @Test
  void should_report_data_sources_registered_as_plain_singletons(CapturedOutput output) {
    audited
        .withInitializer(
            context -> {
              context.getBeanFactory().registerSingleton("firstDataSource", database());
              context.getBeanFactory().registerSingleton("secondDataSource", database());
            })
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(output).contains(INACTIVE, AMBIGUOUS);
  }

  @Test
  void should_find_a_publisher_defined_in_a_parent_context(CapturedOutput output) {
    try (var parent = new AnnotationConfigApplicationContext()) {
      parent.registerBean(InMemoryAuditEventPublisher.class, InMemoryAuditEventPublisher::new);
      parent.refresh();

      audited
          .withParent(parent)
          .withPropertyValues(FAIL_FAST)
          .run(context -> assertThat(context).hasNotFailed());
    }

    assertThat(output).doesNotContain(INACTIVE);
  }

  @Test
  void should_ignore_auditable_declared_only_on_an_interface(CapturedOutput output) {
    runner
        .withBean("ledger", Ledger.class, InterfaceAuditedLedger::new)
        .withPropertyValues(FAIL_FAST)
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(output).doesNotContain(INACTIVE);
  }

  @Test
  void should_report_audited_methods_when_aspectjweaver_is_absent(CapturedOutput output) {
    audited
        .withClassLoader(new FilteredClassLoader("org.aspectj.weaver"))
        .withBean(InMemoryAuditEventPublisher.class, InMemoryAuditEventPublisher::new)
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(output).contains("ERROR", NO_WEAVER, OrderService.class.getName());
  }

  @Test
  void should_report_an_audited_bean_that_is_not_instantiated_yet(CapturedOutput output) {
    runner
        .withBean(
            "orderService",
            OrderService.class,
            OrderService::new,
            definition -> definition.setLazyInit(true))
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(output).contains(INACTIVE, OrderService.class.getName());
  }

  @Test
  void should_stay_silent_when_no_bean_declares_auditable_methods(CapturedOutput output) {
    runner
        .withBean("firstDataSource", DataSource.class, AuditPublisherStartupCheckTest::database)
        .withBean("secondDataSource", DataSource.class, AuditPublisherStartupCheckTest::database)
        .withPropertyValues(FAIL_FAST)
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(output).doesNotContain(INACTIVE);
  }

  @Test
  void should_stay_silent_when_the_application_defines_its_own_publisher(CapturedOutput output) {
    twoDataSources
        .withBean(InMemoryAuditEventPublisher.class, InMemoryAuditEventPublisher::new)
        .withPropertyValues(FAIL_FAST)
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(output).doesNotContain(INACTIVE);
  }

  private static DataSource database() {
    return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID());
  }

  /** Declares {@code @Auditable} on the interface, where the aspect does not see it. */
  interface Ledger {

    @Auditable(entityType = "LEDGER", action = "POST", entityId = "#id")
    void post(String id);
  }

  /** Implements the audited interface method without repeating the annotation. */
  static class InterfaceAuditedLedger implements Ledger {

    @Override
    public void post(String id) {
      // not audited: the aspect only matches annotations on the class's own methods
    }
  }
}
