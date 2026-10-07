package io.codehunters.geo.polygon;

import io.codehunters.geo.MatchedZone;

/**
 * A zone and its outline.
 *
 * @param zone what a match reports
 * @param area the zone's outline
 */
public record GeoZone(MatchedZone zone, Polygon area) {

  /**
   * Checks both parts are present.
   *
   * @throws IllegalArgumentException if either is {@code null}
   */
  public GeoZone {
    if (zone == null || area == null) {
      throw new IllegalArgumentException("A geo zone needs a zone and an area");
    }
  }
}
