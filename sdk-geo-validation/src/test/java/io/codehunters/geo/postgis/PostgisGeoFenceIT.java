package io.codehunters.geo.postgis;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.geo.DenialReason;
import io.codehunters.geo.GeoDecision;
import io.codehunters.geo.GeoPoint;
import io.codehunters.geo.MatchedZone;
import io.codehunters.geo.ZoneRef;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Checks points against the generic zones of {@code geo-zone-fixture.sql} on a real PostGIS. */
class PostgisGeoFenceIT {

  private static final JdbcClient JDBC = JdbcClient.create(PostgisDatabase.dataSource());

  private final PostgisGeoFence zones =
      new PostgisGeoFence(JDBC, PostgisZoneTable.named("geo.zones"));

  private final PostgisGeoFence idOnly =
      new PostgisGeoFence(
          JDBC,
          new PostgisZoneTable(
              "geo.zones",
              new PostgisZoneTable.Columns("scope_ref", "zone_id", "geom", "", "", "")));

  private final PostgisGeoFence geometryColumn =
      new PostgisGeoFence(
          JDBC,
          new PostgisZoneTable(
              "geo.zones_geometry",
              new PostgisZoneTable.Columns("scope_ref", "zone_id", "boundary", "", "", "")));

  @Test
  void should_allow_with_every_attribute_when_the_point_is_inside_a_zone() {
    assertThat(zones.validate(new ZoneRef("north"), GeoPoint.of(1, 1)))
        .isEqualTo(GeoDecision.allowed(MatchedZone.of("city-1", "CITY", "Town", "Region")));
  }

  @Test
  void should_match_the_lowest_zone_id_when_several_zones_cover_the_point() {
    assertThat(zones.validate(new ZoneRef("north"), GeoPoint.of(1.5, 0.5)).zone())
        .map(MatchedZone::id)
        .contains("city-1");
  }

  @Test
  void should_match_the_larger_zone_when_only_it_covers_the_point() {
    assertThat(zones.validate(new ZoneRef("north"), GeoPoint.of(8, 8)))
        .isEqualTo(GeoDecision.allowed(MatchedZone.of("region-1", "REGION", "Region", null)));
  }

  @Test
  void should_allow_when_the_point_is_on_a_vertex_of_the_zone() {
    assertThat(zones.validate(new ZoneRef("edge"), GeoPoint.of(2, 2)).allowed()).isTrue();
  }

  @Test
  void should_deny_as_outside_when_no_zone_covers_the_point() {
    assertThat(zones.validate(new ZoneRef("north"), GeoPoint.of(20, 20)))
        .isEqualTo(GeoDecision.denied(DenialReason.OUTSIDE_ZONE));
  }

  @Test
  void should_deny_as_not_found_when_the_scope_has_no_zones() {
    assertThat(zones.validate(new ZoneRef("south"), GeoPoint.of(1, 1)))
        .isEqualTo(GeoDecision.denied(DenialReason.ZONE_NOT_FOUND));
  }

  @Test
  void should_deny_as_outside_when_the_point_is_inside_a_hole() {
    assertThat(zones.validate(new ZoneRef("lake"), GeoPoint.of(5, 5)))
        .isEqualTo(GeoDecision.denied(DenialReason.OUTSIDE_ZONE));
  }

  @Test
  void should_allow_when_the_point_is_between_the_outline_and_the_hole() {
    assertThat(zones.validate(new ZoneRef("lake"), GeoPoint.of(1, 1)).allowed()).isTrue();
  }

  @Test
  void should_allow_east_of_the_antimeridian_when_the_zone_crosses_it() {
    assertThat(zones.validate(new ZoneRef("pacific"), GeoPoint.of(-15, 179.5)).allowed()).isTrue();
  }

  @Test
  void should_allow_west_of_the_antimeridian_when_the_zone_crosses_it() {
    assertThat(zones.validate(new ZoneRef("pacific"), GeoPoint.of(-15, -175)).allowed()).isTrue();
  }

  @Test
  void should_deny_as_outside_when_the_point_is_on_the_far_side_of_the_globe() {
    assertThat(zones.validate(new ZoneRef("pacific"), GeoPoint.of(-15, 0)))
        .isEqualTo(GeoDecision.denied(DenialReason.OUTSIDE_ZONE));
  }

  @Test
  void should_report_only_the_id_when_no_optional_column_is_configured() {
    assertThat(idOnly.validate(new ZoneRef("north"), GeoPoint.of(1, 1)))
        .isEqualTo(GeoDecision.allowed(MatchedZone.of("city-1", null, null, null)));
  }

  @Test
  void should_allow_when_the_zones_are_stored_in_a_geometry_column() {
    assertThat(geometryColumn.validate(new ZoneRef("north"), GeoPoint.of(1, 1)))
        .isEqualTo(GeoDecision.allowed(MatchedZone.of("city-1", null, null, null)));
  }

  @Test
  void should_deny_as_outside_when_a_geometry_column_zone_does_not_cover_the_point() {
    assertThat(geometryColumn.validate(new ZoneRef("north"), GeoPoint.of(20, 20)))
        .isEqualTo(GeoDecision.denied(DenialReason.OUTSIDE_ZONE));
  }
}
