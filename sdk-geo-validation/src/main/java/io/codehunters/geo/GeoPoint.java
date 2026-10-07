package io.codehunters.geo;

/**
 * An immutable WGS84 coordinate.
 *
 * @param latitude the point's latitude
 * @param longitude the point's longitude
 */
public record GeoPoint(Latitude latitude, Longitude longitude) {

  /**
   * Builds a point from decimal degrees.
   *
   * @param latitude latitude in {@code [-90, 90]}
   * @param longitude longitude in {@code [-180, 180]}
   * @return the point
   * @throws IllegalArgumentException if a coordinate is out of range or not a finite number
   */
  public static GeoPoint of(double latitude, double longitude) {
    return new GeoPoint(new Latitude(latitude), new Longitude(longitude));
  }
}
