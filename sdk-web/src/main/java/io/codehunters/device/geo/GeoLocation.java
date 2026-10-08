package io.codehunters.device.geo;

/**
 * Where an address is believed to be.
 *
 * @param countryCode ISO 3166-1 alpha-2 code, empty when unknown
 * @param region the region or state, empty when unknown
 * @param city the city, empty when unknown
 * @param latitude degrees, -90 to 90
 * @param longitude degrees, -180 to 180
 */
public record GeoLocation(
    String countryCode, String region, String city, double latitude, double longitude) {

  /** Rejects null names and coordinates out of range or not finite. */
  public GeoLocation {
    if (countryCode == null || region == null || city == null) {
      throw new IllegalArgumentException(
          "location names must not be null; use an empty string when unknown");
    }
    if (!(latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180)) {
      throw new IllegalArgumentException("coordinates are out of range");
    }
  }
}
