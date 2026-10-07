package io.codehunters.geo.sqlserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SqlServerProcedureTest {

  @ParameterizedTest
  @ValueSource(strings = {"sp_fence_check", "geo.sp_fence_check", "Geo_1.Check_2"})
  void should_accept_the_name_when_it_is_a_plain_or_schema_qualified_identifier(String name) {
    assertThat(SqlServerProcedure.named(name).name()).isEqualTo(name);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "geo.sp; DROP TABLE x",
        "a.b.c",
        "1sp",
        "[geo].[sp]",
        "geo.",
        "sp check",
        "geo.sp--"
      })
  void should_reject_the_name_without_echoing_it_when_it_is_not_an_identifier(String name) {
    assertThatThrownBy(() -> SqlServerProcedure.named(name))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("The procedure name must be an identifier, optionally schema-qualified");
  }

  @Test
  void should_use_the_default_parameter_names_when_none_are_configured() {
    assertThat(SqlServerProcedure.named("geo.sp").parameters())
        .isEqualTo(
            new SqlServerProcedure.Parameters(
                "scope_ref",
                "lat",
                "lon",
                "allowed",
                "zone_id",
                "zone_type",
                "zone_name",
                "zone_parent"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"@scope", "scope ref", "scope;", "1scope"})
  void should_reject_a_parameter_name_when_it_is_not_an_identifier(String scope) {
    assertThatThrownBy(
            () ->
                new SqlServerProcedure.Parameters(
                    scope, "lat", "lon", "allowed", "zone_id", "", "", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Procedure parameter names must be identifiers without '@': scope, latitude,"
                + " longitude, allowed and zone-id are required");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void should_reject_the_parameters_when_a_required_name_is_missing(String zoneId) {
    assertThatThrownBy(
            () ->
                new SqlServerProcedure.Parameters(
                    "scope_ref", "lat", "lon", "allowed", zoneId, "", "", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("zone-id are required");
  }

  @Test
  void should_treat_an_optional_output_as_absent_when_its_name_is_blank() {
    var parameters =
        new SqlServerProcedure.Parameters(
            "scope_ref", "lat", "lon", "allowed", "zone_id", null, " ", "");

    assertThat(parameters.zoneType()).isEmpty();
    assertThat(parameters.zoneName()).isEmpty();
    assertThat(parameters.zoneParent()).isEmpty();
  }
}
