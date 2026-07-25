package io.codehunters.geo;

/**
 * Outcome of a territorial validation: allow + matched zone, or deny.
 *
 * @param allowed whether the point falls inside an allowed zone
 * @param zoneCode the matched zone's code, or {@code null} when denied
 * @param zoneName the matched zone's name, or {@code null} when denied
 * @param zoneType the matched zone's administrative level, or {@code null} when denied
 * @param department the department the matched zone belongs to, or {@code null} when denied
 * @param denialReason the reason for denial, or {@code null} when allowed
 */
public record GeoValidationDecision(
    boolean allowed,
    String zoneCode,
    String zoneName,
    MatchedZoneType zoneType,
    String department,
    DenialReason denialReason) {

  /**
   * Builds an allowing decision carrying the matched zone.
   *
   * @param zoneCode the matched zone's code
   * @param zoneName the matched zone's name
   * @param zoneType the matched zone's administrative level
   * @param department the department the matched zone belongs to
   * @return an allowing {@link GeoValidationDecision}
   */
  public static GeoValidationDecision allowed(
      String zoneCode, String zoneName, MatchedZoneType zoneType, String department) {
    return new GeoValidationDecision(true, zoneCode, zoneName, zoneType, department, null);
  }

  /**
   * Builds a denying decision with no matched zone.
   *
   * @return a denying {@link GeoValidationDecision} with {@link DenialReason#OUTSIDE_ALLOWED_ZONE}
   */
  public static GeoValidationDecision denied() {
    return new GeoValidationDecision(
        false, null, null, null, null, DenialReason.OUTSIDE_ALLOWED_ZONE);
  }
}
