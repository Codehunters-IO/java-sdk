package io.codehunters.geo.config;

/** Which geofence the SDK registers. */
public enum GeoAdapter {
  /** {@link io.codehunters.geo.polygon.PolygonGeoFence} over the application's zone source. */
  POLYGON,

  /** {@link io.codehunters.geo.postgis.PostgisGeoFence} over the configured zone table. */
  POSTGIS
}
