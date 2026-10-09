package io.codehunters.geo.postgis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Checks that PostgreSQL answers the adapter's statement from the spatial index, on 5000 analysed
 * zones of one scope laid out as the README's reference DDL indexes them.
 */
class PostgisGeoFencePlanIT {

  private static final JdbcClient JDBC = JdbcClient.create(PostgisDatabase.dataSource());

  private static final PostgisZoneTable GEOGRAPHY_TABLE =
      new PostgisZoneTable(
          "geo.dense_zones",
          new PostgisZoneTable.Columns("scope_ref", "zone_id", "geom", "", "", ""));

  private static final PostgisZoneTable GEOMETRY_TABLE =
      new PostgisZoneTable(
          "geo.dense_zones_geometry",
          new PostgisZoneTable.Columns("scope_ref", "zone_id", "boundary", "", "", ""));

  @Test
  void should_use_the_spatial_index_when_the_zones_are_a_geography_column() {
    assertThat(plan(GEOGRAPHY_TABLE)).contains("\"Index Name\": \"idx_dense_geom\"");
  }

  @Test
  void should_use_the_expression_index_when_the_zones_are_a_geometry_column() {
    assertThat(plan(GEOMETRY_TABLE)).contains("\"Index Name\": \"idx_dense_geometry_geog\"");
  }

  private static String plan(PostgisZoneTable table) {
    return JDBC.sql("EXPLAIN (FORMAT JSON) " + PostgisGeoFence.statement(table))
        .param("scope", "dense")
        .param("lat", 0.05)
        .param("lon", 0.05)
        .query(String.class)
        .single();
  }
}
