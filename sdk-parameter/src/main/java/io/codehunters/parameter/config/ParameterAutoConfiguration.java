package io.codehunters.parameter.config;

import io.codehunters.parameter.ParameterClient;
import io.codehunters.parameter.cache.CachingParameterClient;
import io.codehunters.parameter.jdbc.JdbcParameterAdapter;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Auto-configures the parameter SDK against the application's {@link DataSource}. Active when
 * {@link JdbcClient} is on the classpath, there is a single {@link DataSource} candidate and {@code
 * codehunters.parameter.enabled} is not {@code false}.
 */
@AutoConfiguration(
    afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnClass(JdbcClient.class)
@ConditionalOnBooleanProperty(name = "codehunters.parameter.enabled", matchIfMissing = true)
@ConditionalOnSingleCandidate(DataSource.class)
@EnableConfigurationProperties(ParameterProperties.class)
public class ParameterAutoConfiguration {

  private static final String JDBC_CLIENT = "parameterJdbcClient";

  /** Default constructor invoked by the Spring container. */
  public ParameterAutoConfiguration() {}

  /**
   * Builds the {@link JdbcClient} used to query the parameters table. It is not an autowiring
   * candidate, so it never competes with the application's own {@link JdbcClient}.
   *
   * @param dataSource the application's data source
   * @return the configured client
   */
  @Bean(name = JDBC_CLIENT, defaultCandidate = false)
  @ConditionalOnMissingBean(name = JDBC_CLIENT)
  public JdbcClient parameterJdbcClient(DataSource dataSource) {
    return JdbcClient.create(dataSource);
  }

  /**
   * Builds the default {@link ParameterClient}, cached when {@code cache-ttl} is positive.
   *
   * @param jdbcClient the JDBC client targeting the application's data source
   * @param properties the parameter configuration
   * @return the JDBC-backed client
   */
  @Bean
  @ConditionalOnMissingBean(ParameterClient.class)
  public ParameterClient parameterClient(
      @Qualifier(JDBC_CLIENT) JdbcClient jdbcClient, ParameterProperties properties) {
    var jdbc = new JdbcParameterAdapter(jdbcClient, properties.tableName());
    return properties.isCacheEnabled()
        ? new CachingParameterClient(jdbc, properties.cacheTtl())
        : jdbc;
  }
}
