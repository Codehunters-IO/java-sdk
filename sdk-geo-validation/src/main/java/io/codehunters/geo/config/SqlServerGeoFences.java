package io.codehunters.geo.config;

import io.codehunters.geo.GeoFenceValidator;
import io.codehunters.geo.sqlserver.SqlServerGeoFence;
import io.codehunters.geo.sqlserver.SqlServerProcedure;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Keeps spring-jdbc types out of the auto-configuration until the adapter is selected. */
final class SqlServerGeoFences {

  private static final long MILLIS_PER_SECOND = 1000L;

  private SqlServerGeoFences() {}

  static GeoFenceValidator create(
      DataSource dataSource, SqlServerProcedure procedure, Duration queryTimeout) {
    return new SqlServerGeoFence(JdbcClient.create(template(dataSource, queryTimeout)), procedure);
  }

  /** A template of its own, so the timeout never touches the application's templates. */
  static JdbcTemplate template(DataSource dataSource, Duration queryTimeout) {
    JdbcTemplate template = new JdbcTemplate(dataSource);
    template.setQueryTimeout(seconds(queryTimeout));
    return template;
  }

  private static int seconds(Duration timeout) {
    long millis = timeout.toMillis();
    return Math.toIntExact(Math.ceilDiv(millis, MILLIS_PER_SECOND));
  }
}
