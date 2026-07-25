package io.codehunters.geo;

import io.codehunters.geo.persistence.SqlServerGeoValidationAdapter;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Registers the SQL Server geo-validation adapter when a DataSource is available. */
@AutoConfiguration(
    afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "geo.validation", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(GeoValidationProperties.class)
public class GeoValidationAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public GeoValidationAutoConfiguration() {}

  /**
   * Registers the default JDBC-backed {@link RaffleGeoValidationPort} when the consumer has not
   * already provided one.
   *
   * @param dataSource the target data source
   * @return a {@link SqlServerGeoValidationAdapter} wrapping the data source
   */
  @Bean
  @ConditionalOnMissingBean(RaffleGeoValidationPort.class)
  public RaffleGeoValidationPort raffleGeoValidationPort(DataSource dataSource) {
    return new SqlServerGeoValidationAdapter(JdbcClient.create(dataSource));
  }
}
