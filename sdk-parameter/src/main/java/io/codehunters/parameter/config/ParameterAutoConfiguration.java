package io.codehunters.parameter.config;

import io.codehunters.parameter.application.ParameterClient;
import io.codehunters.parameter.infrastructure.JdbcParameterAdapter;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Auto-configures the parameter SDK against the consumer's {@link DataSource}. Active when {@link
 * JdbcClient} is on the classpath and a {@link DataSource} bean is available. Zero configuration
 * required; consumers wanting to opt out can exclude this class via {@code
 * spring.autoconfigure.exclude}.
 */
@AutoConfiguration(
    afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnClass(JdbcClient.class)
@ConditionalOnBean(DataSource.class)
public class ParameterAutoConfiguration {

  /** Default constructor invoked by the Spring container. */
  public ParameterAutoConfiguration() {}

  /**
   * Builds the {@link JdbcClient} used to query the parameters table.
   *
   * @param dataSource the consumer-provided data source
   * @return the configured client
   */
  @Bean
  @ConditionalOnMissingBean(name = "parameterJdbcClient")
  public JdbcClient parameterJdbcClient(DataSource dataSource) {
    return JdbcClient.create(dataSource);
  }

  /**
   * Builds the default {@link ParameterClient} backed by the given {@link JdbcClient}.
   *
   * @param parameterJdbcClient the JDBC client targeting the consumer data source
   * @return the JDBC-backed client
   */
  @Bean
  @ConditionalOnMissingBean(ParameterClient.class)
  public ParameterClient parameterClient(JdbcClient parameterJdbcClient) {
    return new JdbcParameterAdapter(parameterJdbcClient);
  }
}
