package io.codehunters.geo.polygon;

import io.codehunters.geo.DenialReason;
import io.codehunters.geo.GeoDecision;
import io.codehunters.geo.GeoFenceValidator;
import io.codehunters.geo.GeoPoint;
import io.codehunters.geo.ZoneRef;
import java.util.List;

/**
 * Checks a point against the scope's zones, in the order the source returns them, and reports the
 * first zone that contains it. Thread-safe when the source is.
 */
public final class PolygonGeoFence implements GeoFenceValidator {

  private final GeoZoneSource source;

  /**
   * Creates the fence.
   *
   * @param source where the zones of each scope come from
   */
  public PolygonGeoFence(GeoZoneSource source) {
    if (source == null) {
      throw new IllegalArgumentException("A polygon geofence needs a zone source");
    }
    this.source = source;
  }

  @Override
  public GeoDecision validate(ZoneRef scope, GeoPoint point) {
    List<GeoZone> zones = source.zonesFor(scope);
    if (zones == null || zones.isEmpty()) {
      return GeoDecision.denied(DenialReason.ZONE_NOT_FOUND);
    }
    return zones.stream()
        .filter(zone -> zone.area().contains(point))
        .findFirst()
        .map(zone -> GeoDecision.allowed(zone.zone()))
        .orElse(GeoDecision.denied(DenialReason.OUTSIDE_ZONE));
  }
}
