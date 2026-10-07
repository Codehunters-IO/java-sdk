package io.codehunters.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GeoPointTest {
  @Test
  void should_build_point_within_range() {
    GeoPoint p = new GeoPoint(new Latitude(6.2442), new Longitude(-75.5812));
    assertEquals(6.2442, p.latitude().value());
    assertEquals(-75.5812, p.longitude().value());
  }

  @Test
  void should_build_point_from_decimal_degrees_when_using_the_factory() {
    assertEquals(
        new GeoPoint(new Latitude(6.2442), new Longitude(-75.5812)), GeoPoint.of(6.2442, -75.5812));
  }

  @Test
  void should_reject_latitude_out_of_range() {
    assertThrows(IllegalArgumentException.class, () -> new Latitude(91.0));
  }

  @Test
  void should_reject_longitude_out_of_range() {
    assertThrows(IllegalArgumentException.class, () -> new Longitude(-181.0));
  }
}
