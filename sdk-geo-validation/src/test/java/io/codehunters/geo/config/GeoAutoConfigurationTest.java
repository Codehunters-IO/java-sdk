package io.codehunters.geo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.codehunters.geo.DenialReason;
import io.codehunters.geo.GeoDecision;
import io.codehunters.geo.GeoFenceValidator;
import io.codehunters.geo.polygon.GeoZoneSource;
import io.codehunters.geo.polygon.PolygonGeoFence;
import io.codehunters.geo.sqlserver.SqlServerGeoFence;
import io.codehunters.geo.sqlserver.SqlServerProcedure;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

class GeoAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(GeoAutoConfiguration.class));

  @Configuration(proxyBeanMethods = false)
  static class ZoneSource {
    @Bean
    GeoZoneSource zoneSource() {
      return scope -> List.of();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OneDataSource {
    @Bean
    DataSource dataSource() {
      return mock(DataSource.class);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class TwoDataSources {
    @Bean
    DataSource first() {
      return mock(DataSource.class);
    }

    @Bean
    DataSource second() {
      return mock(DataSource.class);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OwnFence {
    @Bean
    GeoFenceValidator ownFence() {
      return (scope, point) -> GeoDecision.denied(DenialReason.OUTSIDE_ZONE);
    }
  }

  @Test
  void should_register_no_geofence_when_no_adapter_is_configured() {
    runner
        .withUserConfiguration(ZoneSource.class, OneDataSource.class)
        .run(context -> assertThat(context).doesNotHaveBean(GeoFenceValidator.class));
  }

  @Test
  void should_register_the_polygon_fence_when_adapter_is_polygon_and_a_zone_source_exists() {
    runner
        .withUserConfiguration(ZoneSource.class)
        .withPropertyValues("codehunters.geo.adapter=polygon")
        .run(
            context ->
                assertThat(context)
                    .getBean(GeoFenceValidator.class)
                    .isInstanceOf(PolygonGeoFence.class));
  }

  @Test
  void should_fail_startup_when_adapter_is_polygon_without_a_zone_source() {
    runner
        .withPropertyValues("codehunters.geo.adapter=polygon")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage("codehunters.geo.adapter=polygon needs exactly one GeoZoneSource bean"));
  }

  @Test
  void should_register_the_sql_server_fence_when_adapter_is_sql_server_with_a_procedure() {
    runner
        .withUserConfiguration(OneDataSource.class)
        .withPropertyValues(
            "codehunters.geo.adapter=sql-server", "codehunters.geo.sql-server.procedure=geo.sp")
        .run(
            context ->
                assertThat(context)
                    .getBean(GeoFenceValidator.class)
                    .isInstanceOf(SqlServerGeoFence.class));
  }

  @Test
  void should_bind_the_parameter_names_when_they_are_configured() {
    runner
        .withUserConfiguration(OneDataSource.class)
        .withPropertyValues(
            "codehunters.geo.adapter=sql-server",
            "codehunters.geo.sql-server.procedure=geo.sp",
            "codehunters.geo.sql-server.parameters.scope=ref",
            "codehunters.geo.sql-server.parameters.zone-parent=")
        .run(
            context ->
                assertThat(context.getBean(GeoProperties.class).sqlServer().procedure())
                    .isEqualTo(
                        new SqlServerProcedure(
                            "geo.sp",
                            new SqlServerProcedure.Parameters(
                                "ref",
                                "lat",
                                "lon",
                                "allowed",
                                "zone_id",
                                "zone_type",
                                "zone_name",
                                ""))));
  }

  @Test
  void should_fail_startup_when_adapter_is_sql_server_without_a_procedure() {
    runner
        .withUserConfiguration(OneDataSource.class)
        .withPropertyValues("codehunters.geo.adapter=sql-server")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage(
                        "codehunters.geo.sql-server.procedure is required when"
                            + " codehunters.geo.adapter=sql-server"));
  }

  @Test
  void should_fail_startup_without_echoing_the_name_when_the_procedure_is_not_an_identifier() {
    runner
        .withUserConfiguration(OneDataSource.class)
        .withPropertyValues(
            "codehunters.geo.adapter=sql-server",
            "codehunters.geo.sql-server.procedure=geo.sp; DROP TABLE zone")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage(
                        "codehunters.geo.sql-server: The procedure name must be an identifier,"
                            + " optionally schema-qualified"));
  }

  @Test
  void should_fail_startup_when_adapter_is_sql_server_and_the_data_source_is_ambiguous() {
    runner
        .withUserConfiguration(TwoDataSources.class)
        .withPropertyValues(
            "codehunters.geo.adapter=sql-server", "codehunters.geo.sql-server.procedure=geo.sp")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage(
                        "codehunters.geo.adapter=sql-server needs exactly one DataSource bean,"
                            + " or a primary one"));
  }

  @Test
  void should_fail_startup_when_adapter_is_sql_server_and_spring_jdbc_is_absent() {
    runner
        .withClassLoader(new FilteredClassLoader(JdbcClient.class))
        .withUserConfiguration(OneDataSource.class)
        .withPropertyValues(
            "codehunters.geo.adapter=sql-server", "codehunters.geo.sql-server.procedure=geo.sp")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage("codehunters.geo.adapter=sql-server needs spring-jdbc on the classpath"));
  }

  @Test
  void should_keep_the_application_geofence_when_one_is_defined() {
    runner
        .withUserConfiguration(OwnFence.class, ZoneSource.class)
        .withPropertyValues("codehunters.geo.adapter=polygon")
        .run(context -> assertThat(context).hasSingleBean(GeoFenceValidator.class).hasBean("ownFence"));
  }

  @Test
  void should_register_nothing_when_the_sdk_is_disabled() {
    runner
        .withUserConfiguration(ZoneSource.class)
        .withPropertyValues("codehunters.geo.enabled=false", "codehunters.geo.adapter=polygon")
        .run(
            context ->
                assertThat(context)
                    .doesNotHaveBean(GeoFenceValidator.class)
                    .doesNotHaveBean(GeoProperties.class));
  }
}
