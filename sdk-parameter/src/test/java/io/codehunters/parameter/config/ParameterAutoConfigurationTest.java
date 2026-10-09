package io.codehunters.parameter.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.parameter.Parameter;
import io.codehunters.parameter.ParameterClient;
import io.codehunters.parameter.ParameterModule;
import io.codehunters.parameter.cache.CachingParameterClient;
import io.codehunters.parameter.cache.ParameterCache;
import io.codehunters.parameter.cache.ParameterCacheMetrics;
import io.codehunters.parameter.jdbc.JdbcParameterAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

class ParameterAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ParameterAutoConfiguration.class))
          .withUserConfiguration(EmbeddedDataSourceConfig.class);

  @Test
  void registers_default_beans_when_datasource_present() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasSingleBean(ParameterClient.class);
          assertThat(ctx).hasBean("parameterJdbcClient");
        });
  }

  @Test
  void skips_beans_when_no_datasource() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ParameterAutoConfiguration.class))
        .run(ctx -> assertThat(ctx).doesNotHaveBean(ParameterClient.class));
  }

  @Test
  void user_provided_client_wins() {
    runner
        .withUserConfiguration(CustomClientConfig.class)
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(ParameterClient.class);
              assertThat(ctx.getBean(ParameterClient.class))
                  .isSameAs(CustomClientConfig.CUSTOM_INSTANCE);
            });
  }

  @Test
  void user_provided_jdbc_client_wins() {
    runner
        .withUserConfiguration(CustomJdbcClientConfig.class)
        .run(
            ctx -> {
              assertThat(ctx).hasBean("parameterJdbcClient");
              assertThat(ctx.getBean("parameterJdbcClient", JdbcClient.class))
                  .isSameAs(CustomJdbcClientConfig.CUSTOM_INSTANCE);
            });
  }

  @Test
  void should_keep_the_application_jdbc_client_as_the_only_autowire_candidate() {
    JdbcClient appClient = JdbcClient.create(new EmbeddedDataSourceConfig().dataSource());
    runner
        .withBean("appJdbcClient", JdbcClient.class, () -> appClient)
        .withBean(JdbcClientConsumer.class)
        .run(ctx -> assertThat(ctx.getBean(JdbcClientConsumer.class).client()).isSameAs(appClient));
  }

  @Test
  void should_back_off_when_disabled() {
    runner
        .withPropertyValues("codehunters.parameter.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(ParameterClient.class));
  }

  @Test
  void should_back_off_when_two_data_sources_have_no_primary() {
    runner
        .withBean("second", DataSource.class, () -> new EmbeddedDataSourceConfig().dataSource())
        .run(ctx -> assertThat(ctx).hasNotFailed().doesNotHaveBean(ParameterClient.class));
  }

  @Test
  void should_read_without_cache_by_default() {
    runner.run(
        ctx ->
            assertThat(ctx.getBean(ParameterClient.class))
                .isInstanceOf(JdbcParameterAdapter.class));
  }

  @Test
  void should_cache_when_a_ttl_is_configured() {
    runner
        .withPropertyValues("codehunters.parameter.cache-ttl=5m")
        .run(
            ctx ->
                assertThat(ctx.getBean(ParameterClient.class))
                    .isInstanceOf(CachingParameterClient.class));
  }

  @Test
  void should_read_without_cache_when_the_cache_ttl_is_empty() {
    runner
        .withPropertyValues("codehunters.parameter.cache-ttl=")
        .run(
            ctx ->
                assertThat(ctx.getBean(ParameterClient.class))
                    .isInstanceOf(JdbcParameterAdapter.class));
  }

  @Test
  void should_register_a_disabled_cache_by_default() {
    runner.run(ctx -> assertThat(ctx.getBean(ParameterCache.class).isEnabled()).isFalse());
  }

  @Test
  void should_register_an_enabled_cache_when_a_ttl_is_configured() {
    runner
        .withPropertyValues("codehunters.parameter.cache-ttl=5m")
        .run(ctx -> assertThat(ctx.getBean(ParameterCache.class).isEnabled()).isTrue());
  }

  @Test
  void should_fail_startup_on_a_cache_size_below_one() {
    runner
        .withPropertyValues(
            "codehunters.parameter.cache-ttl=5m", "codehunters.parameter.cache-max-entries=0")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void should_bind_cache_metrics_when_a_meter_registry_exists() {
    runner
        .withPropertyValues("codehunters.parameter.cache-ttl=5m")
        .withBean(SimpleMeterRegistry.class)
        .run(
            ctx ->
                assertThat(
                        ctx.getBean(SimpleMeterRegistry.class)
                            .find("cache.gets")
                            .tag("cache", ParameterCacheMetrics.CACHE_NAME)
                            .meters())
                    .isNotEmpty());
  }

  @Test
  void should_cache_without_micrometer_on_the_classpath() {
    runner
        .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
        .withPropertyValues("codehunters.parameter.cache-ttl=5m")
        .run(
            ctx ->
                assertThat(ctx.getBean(ParameterClient.class))
                    .isInstanceOf(CachingParameterClient.class));
  }

  @Test
  void should_fail_startup_on_a_negative_cache_ttl() {
    runner
        .withPropertyValues("codehunters.parameter.cache-ttl=-1s")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void should_fail_startup_on_a_table_name_that_is_not_an_identifier() {
    runner
        .withPropertyValues("codehunters.parameter.table-name=parameters;drop")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void should_start_with_boot_data_source_auto_configuration() {
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                DataSourceAutoConfiguration.class, ParameterAutoConfiguration.class))
        .withPropertyValues("spring.datasource.url=jdbc:h2:mem:boot-" + UUID.randomUUID())
        .run(ctx -> assertThat(ctx).hasSingleBean(ParameterClient.class));
  }

  record JdbcClientConsumer(JdbcClient client) {}

  @Configuration
  static class EmbeddedDataSourceConfig {
    @Bean
    DataSource dataSource() {
      JdbcDataSource ds = new JdbcDataSource();
      ds.setURL("jdbc:h2:mem:autoconfig-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
      ds.setUser("sa");
      return ds;
    }
  }

  @Configuration
  static class CustomClientConfig {
    static final ParameterClient CUSTOM_INSTANCE =
        new ParameterClient() {
          @Override
          public Optional<Parameter> lookup(ParameterModule module, String submodule, String name) {
            return Optional.empty();
          }

          @Override
          public List<Parameter> findByModule(ParameterModule module, String submodule) {
            return List.of();
          }

          @Override
          public List<Parameter> findByModules(Collection<ParameterModule> modules) {
            return List.of();
          }

          @Override
          public List<Parameter> findByModuleAndSubmodules(
              ParameterModule module, Collection<String> submodules) {
            return List.of();
          }

          @Override
          public Map<String, String> asMap(ParameterModule module, String submodule) {
            return Map.of();
          }

          @Override
          public Map<String, Parameter> asMapByName(ParameterModule module, String submodule) {
            return Map.of();
          }
        };

    @Bean
    ParameterClient customClient() {
      return CUSTOM_INSTANCE;
    }

    @Bean
    JdbcClient placeholderJdbcClient(DataSource dataSource) {
      return JdbcClient.create(dataSource);
    }
  }

  @Configuration
  static class CustomJdbcClientConfig {
    static final JdbcClient CUSTOM_INSTANCE =
        JdbcClient.create(new EmbeddedDataSourceConfig().dataSource());

    @Bean
    JdbcClient parameterJdbcClient() {
      return CUSTOM_INSTANCE;
    }
  }
}
