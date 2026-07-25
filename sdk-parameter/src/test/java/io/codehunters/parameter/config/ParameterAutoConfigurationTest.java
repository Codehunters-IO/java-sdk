package io.codehunters.parameter.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.parameter.application.ParameterClient;
import io.codehunters.parameter.domain.model.Parameter;
import io.codehunters.parameter.domain.model.ParameterModule;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
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
