package io.codehunters.geo.config;

import io.codehunters.geo.GeoFenceValidator;
import io.codehunters.geo.postgis.PostgisGeoFence;
import io.codehunters.geo.postgis.PostgisZoneTable;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Keeps spring-jdbc types out of the auto-configuration until the PostGIS adapter is selected. */
final class JdbcGeoFences {

  private static final long MILLIS_PER_SECOND = 1000L;

  private JdbcGeoFences() {}

  static GeoFenceValidator postgis(
      DataSource dataSource, PostgisZoneTable table, Duration queryTimeout) {
    return new PostgisGeoFence(client(dataSource, queryTimeout), table);
  }

  /** A template of its own, so the timeout never touches the application's templates. */
  static JdbcTemplate template(DataSource dataSource, Duration queryTimeout) {
    JdbcTemplate template = new JdbcTemplate(dataSource);
    template.setQueryTimeout(seconds(queryTimeout));
    return template;
  }

  private static JdbcClient client(DataSource dataSource, Duration queryTimeout) {
    return JdbcClient.create(template(dataSource, queryTimeout));
  }

  private static int seconds(Duration timeout) {
    long millis = timeout.toMillis();
    return Math.toIntExact(Math.ceilDiv(millis, MILLIS_PER_SECOND));
  }
}
