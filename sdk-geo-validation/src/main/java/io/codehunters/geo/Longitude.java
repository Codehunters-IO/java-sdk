package io.codehunters.geo;

/**
 * WGS84 longitude in decimal degrees, {@code [-180, 180]}.
 *
 * @param value the longitude in decimal degrees
 */
public record Longitude(double value) {

  /**
   * Validates the longitude is within the WGS84 range.
   *
   * @throws IllegalArgumentException if {@code value} is outside {@code [-180, 180]}
   */
  public Longitude {
    if (value < -180.0 || value > 180.0) {
      throw new IllegalArgumentException("Longitude out of range: " + value);
    }
  }
}
