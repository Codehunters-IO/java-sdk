package io.codehunters.geo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.geo.GeoFenceValidator;
import io.codehunters.geo.GeoPoint;
import io.codehunters.geo.ZoneRef;
import io.codehunters.geo.postgis.PostgisDatabase;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessException;

/** The {@code postgis} adapter wired by the auto-configuration on a real PostGIS. */
class GeoAutoConfigurationPostgisIT {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(GeoAutoConfiguration.class))
          .withBean(DataSource.class, PostgisDatabase::dataSource)
          .withPropertyValues("codehunters.geo.adapter=postgis");

  @Test
  void should_answer_from_the_configured_table_when_the_adapter_is_postgis() {
    runner
        .withPropertyValues("codehunters.geo.postgis.table=geo.zones")
        .run(
            context ->
                assertThat(
                        context
                            .getBean(GeoFenceValidator.class)
                            .validate(new ZoneRef("north"), GeoPoint.of(1, 1))
                            .allowed())
                    .isTrue());
  }

  @Test
  void should_fail_the_check_when_the_query_outlasts_the_timeout() {
    runner
        .withPropertyValues(
            "codehunters.geo.postgis.table=geo.slow_zones",
            "codehunters.geo.postgis.query-timeout=1s")
        .run(
            context ->
                assertThatThrownBy(
                        () ->
                            context
                                .getBean(GeoFenceValidator.class)
                                .validate(new ZoneRef("north"), GeoPoint.of(1, 1)))
                    .isInstanceOf(DataAccessException.class));
  }
}
