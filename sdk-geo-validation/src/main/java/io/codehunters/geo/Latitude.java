package io.codehunters.geo;

/**
 * WGS84 latitude in decimal degrees, {@code [-90, 90]}.
 *
 * @param value the latitude in decimal degrees
 */
public record Latitude(double value) {

  /**
   * Validates the latitude is within the WGS84 range.
   *
   * @throws IllegalArgumentException if {@code value} is outside {@code [-90, 90]}
   */
  public Latitude {
    if (value < -90.0 || value > 90.0) {
      throw new IllegalArgumentException("Latitude out of range: " + value);
    }
  }
}
