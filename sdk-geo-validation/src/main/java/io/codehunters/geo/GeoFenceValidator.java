package io.codehunters.geo;

/**
 * Port: decides whether a point falls inside the zones of a scope. The SDK ships a polygon
 * implementation fed by the caller's zones and a PostGIS one that reads a zone table; a service may
 * supply its own.
 */
@FunctionalInterface
public interface GeoFenceValidator {

  /**
   * Checks the point against the scope's zones.
   *
   * @param scope the scope whose zones apply
   * @param point the coordinate to check
   * @return the decision; never {@code null}
   */
  GeoDecision validate(ZoneRef scope, GeoPoint point);

  /**
   * Checks the point unless the scope covers every point, in which case no zone is looked up.
   *
   * @param scope the scope whose zones apply
   * @param point the coordinate to check
   * @param coverage how much of the map the scope covers
   * @return {@link GeoDecision#unrestricted()} for {@link Coverage#UNRESTRICTED}, otherwise the
   *     result of {@link #validate(ZoneRef, GeoPoint)}
   */
  default GeoDecision validate(ZoneRef scope, GeoPoint point, Coverage coverage) {
    if (coverage == Coverage.UNRESTRICTED) {
      return GeoDecision.unrestricted();
    }
    return validate(scope, point);
  }
}
