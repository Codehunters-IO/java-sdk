package io.codehunters.geo.polygon;

import io.codehunters.geo.GeoPoint;
import java.util.List;

/** Ring fixtures for the polygon tests. */
final class Rings {

  private Rings() {}

  /** An axis-aligned square ring with its lower-left corner at (lat, lon). */
  static List<GeoPoint> square(double latitude, double longitude, double side) {
    return List.of(
        GeoPoint.of(latitude, longitude),
        GeoPoint.of(latitude + side, longitude),
        GeoPoint.of(latitude + side, longitude + side),
        GeoPoint.of(latitude, longitude + side));
  }
}
