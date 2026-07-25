package io.codehunters.audit;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class AuditJdbcAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(AuditJdbcAutoConfiguration.class, AuditAutoConfiguration.class))
          .withUserConfiguration(DataSourceConfig.class);

  @Test
  void should_create_jdbc_adapter_when_datasource_present() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasSingleBean(AuditEventPublisherPort.class);
          assertThat(ctx).hasBean("auditEventJdbcAdapter");
        });
  }

  @Test
  void should_create_audit_aspect_when_jdbc_adapter_exists() {
    runner.run(ctx -> assertThat(ctx).hasSingleBean(AuditAspect.class));
  }

  @Test
  void should_back_off_when_custom_publisher_provided() {
    runner
        .withBean(AuditEventPublisherPort.class, () -> event -> {})
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(AuditEventPublisherPort.class);
              assertThat(ctx).doesNotHaveBean("auditEventJdbcAdapter");
            });
  }

  @Test
  void should_not_create_jdbc_adapter_when_no_datasource() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(AuditJdbcAutoConfiguration.class, AuditAutoConfiguration.class))
        .run(ctx -> assertThat(ctx).doesNotHaveBean(AuditEventPublisherPort.class));
  }

  @Test
  void should_create_default_json_mapper() {
    runner.run(ctx -> assertThat(ctx).hasBean("auditJsonMapper"));
  }

  @Test
  void should_create_default_task_executor() {
    runner.run(ctx -> assertThat(ctx).hasBean("auditTaskExecutor"));
  }

  @Test
  void should_back_off_json_mapper_when_already_provided() {
    runner
        .withBean(
            tools.jackson.databind.json.JsonMapper.class,
            () -> tools.jackson.databind.json.JsonMapper.builder().build())
        .run(ctx -> assertThat(ctx).doesNotHaveBean("auditJsonMapper"));
  }

  @Configuration(proxyBeanMethods = false)
  static class DataSourceConfig {

    @Bean
    DataSource dataSource() {
      return new EmbeddedDatabaseBuilder()
          .setType(EmbeddedDatabaseType.H2)
          .generateUniqueName(true)
          .build();
    }
  }
}
