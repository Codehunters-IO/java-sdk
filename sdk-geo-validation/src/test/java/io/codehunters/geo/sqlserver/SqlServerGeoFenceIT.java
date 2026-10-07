package io.codehunters.geo.sqlserver;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.geo.DenialReason;
import io.codehunters.geo.GeoDecision;
import io.codehunters.geo.GeoPoint;
import io.codehunters.geo.MatchedZone;
import io.codehunters.geo.ZoneRef;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Calls generic sample procedures on a real SQL Server. Uses azure-sql-edge for ARM compatibility,
 * pinned to its final release.
 */
@Testcontainers
class SqlServerGeoFenceIT {

  @Container
  static final MSSQLServerContainer DB =
      new MSSQLServerContainer(
              DockerImageName.parse("mcr.microsoft.com/azure-sql-edge:1.0.7")
                  .asCompatibleSubstituteFor("mcr.microsoft.com/mssql/server"))
          .acceptLicense();

  private static JdbcClient jdbc;

  private static SqlServerGeoFence custom;
  private static SqlServerGeoFence minimal;

  @BeforeAll
  static void loadFixture() throws IOException {
    DataSource dataSource =
        new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    jdbc = JdbcClient.create(dataSource);
    Arrays.stream(fixture().split("(?m)^GO\\s*$"))
        .filter(batch -> !batch.isBlank())
        .forEach(batch -> jdbc.sql(batch).update());
    custom =
        new SqlServerGeoFence(
            jdbc,
            new SqlServerProcedure(
                "geo.sp_fence_check",
                new SqlServerProcedure.Parameters(
                    "ref",
                    "latitude",
                    "longitude",
                    "is_allowed",
                    "matched_id",
                    "matched_kind",
                    "matched_label",
                    "matched_parent")));
    minimal =
        new SqlServerGeoFence(
            jdbc,
            new SqlServerProcedure(
                "geo.sp_minimal",
                new SqlServerProcedure.Parameters(
                    "scope_ref", "lat", "lon", "allowed", "zone_id", "", "", "")));
  }

  @Test
  void should_allow_with_every_attribute_when_the_point_is_inside_a_zone() {
    assertThat(custom.validate(new ZoneRef("north"), GeoPoint.of(1, 1)))
        .isEqualTo(GeoDecision.allowed(MatchedZone.of("city-1", "CITY", "Town", "Region")));
  }

  @Test
  void should_keep_the_type_as_text_when_the_procedure_returns_an_unknown_one() {
    assertThat(custom.validate(new ZoneRef("north"), GeoPoint.of(8, 8)))
        .isEqualTo(GeoDecision.allowed(MatchedZone.of("region-1", "HAMLET", "Region", null)));
  }

  @Test
  void should_deny_as_outside_when_no_zone_contains_the_point() {
    assertThat(custom.validate(new ZoneRef("north"), GeoPoint.of(20, 20)))
        .isEqualTo(GeoDecision.denied(DenialReason.OUTSIDE_ZONE));
  }

  @Test
  void should_deny_as_not_found_when_the_procedure_reports_no_zones_with_a_null_flag() {
    assertThat(custom.validate(new ZoneRef("south"), GeoPoint.of(1, 1)))
        .isEqualTo(GeoDecision.denied(DenialReason.ZONE_NOT_FOUND));
  }

  @Test
  void should_deny_as_outside_when_the_procedure_allows_without_a_zone_id() {
    assertThat(custom.validate(new ZoneRef("broken"), GeoPoint.of(1, 1)))
        .isEqualTo(GeoDecision.denied(DenialReason.OUTSIDE_ZONE));
  }

  @Test
  void should_report_only_the_id_when_the_procedure_has_no_optional_outputs() {
    assertThat(minimal.validate(new ZoneRef("north"), GeoPoint.of(1, 1)))
        .isEqualTo(GeoDecision.allowed(MatchedZone.of("city-1", null, null, null)));
  }

  private static String fixture() throws IOException {
    try (InputStream in =
        SqlServerGeoFenceIT.class.getResourceAsStream("/sqlserver/geo-fence-fixture.sql")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
