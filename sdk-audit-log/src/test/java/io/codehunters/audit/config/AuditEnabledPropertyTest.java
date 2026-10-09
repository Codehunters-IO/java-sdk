package io.codehunters.audit.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.audit.AuditContextProvider;
import io.codehunters.audit.AuditEventPublisherPort;
import io.codehunters.audit.internal.aspect.AuditAspect;
import io.codehunters.audit.internal.aspect.OrderService;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** {@code codehunters.audit.enabled} turns the whole SDK off, as in the other modules. */
@ExtendWith(OutputCaptureExtension.class)
class AuditEnabledPropertyTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  AopAutoConfiguration.class,
                  AuditJdbcAutoConfiguration.class,
                  AuditAutoConfiguration.class))
          .withBean("orderService", OrderService.class, OrderService::new);

  @Test
  void should_configure_auditing_when_enabled_is_not_set() {
    runner
        .withBean("dataSource", DataSource.class, AuditEnabledPropertyTest::database)
        .run(
            context -> {
              assertThat(context).hasSingleBean(AuditEventPublisherPort.class);
              assertThat(context).hasSingleBean(AuditAspect.class);
            });
  }

  @Test
  void should_configure_nothing_when_enabled_is_false() {
    runner
        .withBean("dataSource", DataSource.class, AuditEnabledPropertyTest::database)
        .withPropertyValues("codehunters.audit.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(AuditEventPublisherPort.class);
              assertThat(context).doesNotHaveBean(AuditAspect.class);
              assertThat(context).doesNotHaveBean(AuditContextProvider.class);
            });
  }

  @Test
  void should_not_run_startup_checks_when_enabled_is_false(CapturedOutput output) {
    runner
        .withPropertyValues(
            "codehunters.audit.enabled=false", "codehunters.audit.fail-on-error=true")
        .run(context -> assertThat(context).hasNotFailed());

    assertThat(output).doesNotContain("no AuditEventPublisherPort");
  }

  private static DataSource database() {
    return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID());
  }
}
