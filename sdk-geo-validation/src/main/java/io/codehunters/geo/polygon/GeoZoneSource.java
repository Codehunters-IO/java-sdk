package io.codehunters.geo.polygon;

import io.codehunters.geo.ZoneRef;
import java.util.List;

/**
 * Port: the zones of a scope, supplied by the service that owns them (a table, a file, a cache).
 * Called on every check, so implementations should cache their zones.
 */
@FunctionalInterface
public interface GeoZoneSource {

  /**
   * Returns the scope's zones, in the order they are tried.
   *
   * @param scope the scope
   * @return the zones; empty (or {@code null}) when the scope has none
   */
  List<GeoZone> zonesFor(ZoneRef scope);
}
