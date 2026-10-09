package io.codehunters.geo.postgis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PostgisZoneTableTest {

  @ParameterizedTest
  @ValueSource(strings = {"zones", "geo.zones", "Geo_1.Zone_2"})
  void should_accept_the_name_when_it_is_a_plain_or_schema_qualified_identifier(String name) {
    assertThat(PostgisZoneTable.named(name).name()).isEqualTo(name);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "geo.zones; DROP TABLE x",
        "a.b.c",
        "1zones",
        "\"geo\".\"zones\"",
        "geo.",
        "zone table",
        "geo.zones--"
      })
  void should_reject_the_name_without_echoing_it_when_it_is_not_an_identifier(String name) {
    assertThatThrownBy(() -> PostgisZoneTable.named(name))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("The zone table name must be an identifier, optionally schema-qualified");
  }

  @Test
  void should_reject_the_name_when_a_part_is_longer_than_postgres_allows() {
    assertThatThrownBy(() -> PostgisZoneTable.named("z".repeat(64)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("The zone table name must be an identifier, optionally schema-qualified");
  }

  @Test
  void should_use_the_default_column_names_when_none_are_configured() {
    assertThat(PostgisZoneTable.named("geo.zones").columns())
        .isEqualTo(
            new PostgisZoneTable.Columns(
                "scope_ref", "zone_id", "geom", "zone_type", "zone_name", "zone_parent"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"scope\"", "scope ref", "scope;", "1scope"})
  void should_reject_a_column_name_when_it_is_not_an_identifier(String scope) {
    assertThatThrownBy(() -> new PostgisZoneTable.Columns(scope, "zone_id", "geom", "", "", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Zone table column names must be identifiers: scope, zone-id and geometry are"
                + " required");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void should_reject_the_columns_when_a_required_name_is_missing(String geometry) {
    assertThatThrownBy(
            () -> new PostgisZoneTable.Columns("scope_ref", "zone_id", geometry, "", "", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("geometry are required");
  }

  @Test
  void should_treat_an_optional_column_as_absent_when_its_name_is_blank() {
    var columns = new PostgisZoneTable.Columns("scope_ref", "zone_id", "geom", null, " ", "");

    assertThat(columns.zoneType()).isEmpty();
    assertThat(columns.zoneName()).isEmpty();
    assertThat(columns.zoneParent()).isEmpty();
  }

  @Test
  void should_use_the_default_columns_when_none_are_given() {
    assertThat(new PostgisZoneTable("zones", null).columns())
        .isEqualTo(PostgisZoneTable.Columns.DEFAULTS);
  }
}
