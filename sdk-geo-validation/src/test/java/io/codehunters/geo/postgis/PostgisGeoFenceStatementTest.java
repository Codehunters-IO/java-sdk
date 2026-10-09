package io.codehunters.geo.postgis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostgisGeoFenceStatementTest {

  @Test
  void should_quote_the_schema_and_the_table_when_the_name_is_qualified() {
    assertThat(PostgisGeoFence.statement(PostgisZoneTable.named("geo.zones")))
        .contains("FROM \"geo\".\"zones\"");
  }

  @Test
  void should_quote_an_unqualified_table_name() {
    assertThat(PostgisGeoFence.statement(PostgisZoneTable.named("zones")))
        .contains("FROM \"zones\"");
  }

  @Test
  void should_bind_the_scope_and_the_coordinates_when_building_the_statement() {
    assertThat(PostgisGeoFence.statement(PostgisZoneTable.named("zones")))
        .contains("WHERE \"scope_ref\" = :scope")
        .contains("ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography");
  }

  @Test
  void should_test_cover_on_the_geography_when_building_the_statement() {
    assertThat(PostgisGeoFence.statement(PostgisZoneTable.named("zones")))
        .contains("ST_Covers(geom::geography,");
  }

  @Test
  void should_pick_the_lowest_zone_id_when_several_zones_cover_the_point() {
    assertThat(PostgisGeoFence.statement(PostgisZoneTable.named("zones")))
        .contains("ORDER BY zone_id LIMIT 1");
  }

  @Test
  void should_select_every_configured_column_when_all_are_named() {
    var table =
        new PostgisZoneTable(
            "geo.zones",
            new PostgisZoneTable.Columns("ref", "id", "area", "kind", "label", "owner"));

    assertThat(PostgisGeoFence.statement(table))
        .contains("\"id\" AS zone_id")
        .contains("\"kind\" AS zone_type")
        .contains("\"label\" AS zone_name")
        .contains("\"owner\" AS zone_parent")
        .contains("\"area\" AS geom")
        .contains("WHERE \"ref\" = :scope");
  }

  @Test
  void should_select_null_when_an_optional_column_is_blank() {
    var table =
        new PostgisZoneTable(
            "zones", new PostgisZoneTable.Columns("scope_ref", "zone_id", "geom", "", "", ""));

    assertThat(PostgisGeoFence.statement(table))
        .contains("CAST(NULL AS text) AS zone_type")
        .contains("CAST(NULL AS text) AS zone_name")
        .contains("CAST(NULL AS text) AS zone_parent");
  }
}
