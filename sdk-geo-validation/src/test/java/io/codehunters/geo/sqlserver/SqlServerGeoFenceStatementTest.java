package io.codehunters.geo.sqlserver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SqlServerGeoFenceStatementTest {

  @Test
  void should_suppress_row_counts_before_calling_the_procedure() {
    assertThat(SqlServerGeoFence.statement(SqlServerProcedure.named("geo.sp")))
        .startsWith("SET NOCOUNT ON;");
  }

  @Test
  void should_bind_values_and_name_every_configured_parameter_when_all_outputs_are_used() {
    var procedure =
        new SqlServerProcedure(
            "geo.sp_fence_check",
            new SqlServerProcedure.Parameters(
                "ref", "latitude", "longitude", "is_allowed", "id", "kind", "label", "owner"));

    assertThat(SqlServerGeoFence.statement(procedure))
        .contains("EXEC geo.sp_fence_check @ref = :scope, @latitude = :lat, @longitude = :lon,")
        .contains("@is_allowed = @r_allowed OUTPUT, @id = @r_zone_id OUTPUT")
        .contains("@kind = @r_zone_type OUTPUT")
        .contains("@label = @r_zone_name OUTPUT")
        .contains("@owner = @r_zone_parent OUTPUT;");
  }

  @Test
  void should_leave_an_output_out_of_the_call_when_its_name_is_blank() {
    var procedure =
        new SqlServerProcedure(
            "geo.sp_minimal",
            new SqlServerProcedure.Parameters(
                "scope_ref", "lat", "lon", "allowed", "zone_id", "", "", ""));

    assertThat(SqlServerGeoFence.statement(procedure))
        .contains("@zone_id = @r_zone_id OUTPUT;")
        .doesNotContain("@r_zone_type OUTPUT")
        .doesNotContain("@r_zone_name OUTPUT")
        .doesNotContain("@r_zone_parent OUTPUT");
  }
}
