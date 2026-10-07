package io.codehunters.geo.polygon;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.geo.DenialReason;
import io.codehunters.geo.GeoDecision;
import io.codehunters.geo.GeoPoint;
import io.codehunters.geo.MatchedZone;
import io.codehunters.geo.ZoneRef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PolygonGeoFenceTest {

  private static final ZoneRef NORTH = new ZoneRef("north");
  private static final ZoneRef EMPTY = new ZoneRef("empty");
  private static final ZoneRef UNKNOWN = new ZoneRef("unknown");

  private static final MatchedZone CITY = MatchedZone.of("city-1", "CITY", "Town", "Region");
  private static final MatchedZone REGION = MatchedZone.of("region-1", "REGION", "Region", null);

  private static final Map<ZoneRef, List<GeoZone>> ZONES =
      Map.of(
          NORTH,
          List.of(
              new GeoZone(CITY, Polygon.of(Rings.square(0, 0, 2))),
              new GeoZone(REGION, Polygon.of(Rings.square(0, 0, 10)))),
          EMPTY,
          List.of());

  private final PolygonGeoFence fence =
      new PolygonGeoFence(scope -> ZONES.getOrDefault(scope, null));

  @Test
  void should_allow_with_the_first_matching_zone_when_zones_overlap() {
    assertThat(fence.validate(NORTH, GeoPoint.of(1, 1))).isEqualTo(GeoDecision.allowed(CITY));
  }

  @Test
  void should_allow_with_a_later_zone_when_only_that_one_contains_the_point() {
    assertThat(fence.validate(NORTH, GeoPoint.of(8, 8))).isEqualTo(GeoDecision.allowed(REGION));
  }

  @Test
  void should_deny_as_outside_when_no_zone_contains_the_point() {
    assertThat(fence.validate(NORTH, GeoPoint.of(20, 20)))
        .isEqualTo(GeoDecision.denied(DenialReason.OUTSIDE_ZONE));
  }

  @Test
  void should_deny_as_not_found_when_the_scope_has_no_zones() {
    assertThat(fence.validate(EMPTY, GeoPoint.of(1, 1)))
        .isEqualTo(GeoDecision.denied(DenialReason.ZONE_NOT_FOUND));
  }

  @Test
  void should_deny_as_not_found_when_the_source_returns_null() {
    assertThat(fence.validate(UNKNOWN, GeoPoint.of(1, 1)))
        .isEqualTo(GeoDecision.denied(DenialReason.ZONE_NOT_FOUND));
  }
}
