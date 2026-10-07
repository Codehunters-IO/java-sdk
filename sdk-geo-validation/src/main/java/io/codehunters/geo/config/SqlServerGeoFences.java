package io.codehunters.geo.config;

import io.codehunters.geo.GeoFenceValidator;
import io.codehunters.geo.sqlserver.SqlServerGeoFence;
import io.codehunters.geo.sqlserver.SqlServerProcedure;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Keeps spring-jdbc types out of the auto-configuration until the adapter is selected. */
final class SqlServerGeoFences {

  private SqlServerGeoFences() {}

  static GeoFenceValidator create(DataSource dataSource, SqlServerProcedure procedure) {
    return new SqlServerGeoFence(JdbcClient.create(dataSource), procedure);
  }
}
