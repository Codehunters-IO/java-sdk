package io.codehunters.geo.polygon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.geo.GeoPoint;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PolygonTest {

  /** Square from (0,0) to (10,10) in (lat, lon) with a hole from (4,4) to (6,6). */
  private static final Polygon SQUARE_WITH_HOLE =
      Polygon.of(Rings.square(0, 0, 10)).withHole(Rings.square(4, 4, 2));

  /** An L shape: the square (0,0)-(10,10) without its upper-right quarter (5..10, 5..10). */
  private static final Polygon L_SHAPE =
      Polygon.of(
          List.of(
              GeoPoint.of(0, 0),
              GeoPoint.of(10, 0),
              GeoPoint.of(10, 5),
              GeoPoint.of(5, 5),
              GeoPoint.of(5, 10),
              GeoPoint.of(0, 10)));

  @ParameterizedTest(name = "({0}, {1}) inside={2}")
  @CsvSource({
    "2, 2, true",
    "8, 8, true",
    "15, 5, false",
    "-1, 5, false",
    "5, 5, false",
    "0, 5, true",
    "0, 0, true",
    "10, 10, true",
    "4, 5, true",
    "5, 6, true",
    "10.0000001, 5, false"
  })
  void should_report_containment_when_the_point_is_inside_outside_on_an_edge_or_in_a_hole(
      double latitude, double longitude, boolean expected) {
    assertThat(SQUARE_WITH_HOLE.contains(GeoPoint.of(latitude, longitude))).isEqualTo(expected);
  }

  @ParameterizedTest(name = "({0}, {1}) inside={2}")
  @CsvSource({"2, 8, true", "8, 2, true", "8, 8, false", "5, 7, true", "7, 5, true"})
  void should_follow_the_outline_when_the_polygon_is_concave(
      double latitude, double longitude, boolean expected) {
    assertThat(L_SHAPE.contains(GeoPoint.of(latitude, longitude))).isEqualTo(expected);
  }

  @Test
  void should_accept_the_ring_when_it_repeats_the_first_vertex_at_the_end() {
    Polygon closed =
        Polygon.of(
            List.of(
                GeoPoint.of(0, 0),
                GeoPoint.of(0, 10),
                GeoPoint.of(10, 10),
                GeoPoint.of(0, 0)));

    assertThat(closed.contains(GeoPoint.of(3, 7))).isTrue();
  }

  @Test
  void should_reject_the_ring_when_it_has_fewer_than_three_distinct_vertices() {
    assertThatThrownBy(
            () -> Polygon.of(List.of(GeoPoint.of(0, 0), GeoPoint.of(1, 1), GeoPoint.of(0, 0))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("A polygon ring needs at least 3 distinct vertices");
  }

  @Test
  void should_reject_the_ring_when_an_edge_crosses_the_antimeridian() {
    assertThatThrownBy(
            () ->
                Polygon.of(
                    List.of(GeoPoint.of(0, 170), GeoPoint.of(0, -170), GeoPoint.of(10, -170))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "A polygon edge must not cross the antimeridian; split the zone at longitude 180");
  }

  @Test
  void should_reject_the_ring_when_it_is_null() {
    assertThatThrownBy(() -> Polygon.of(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("A polygon ring must not be null or contain null vertices");
  }
}
