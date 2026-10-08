package io.codehunters.geo.config;

/** Which geofence the SDK registers. */
public enum GeoAdapter {
  /** {@link io.codehunters.geo.polygon.PolygonGeoFence} over the application's zone source. */
  POLYGON,

  /** {@link io.codehunters.geo.sqlserver.SqlServerGeoFence} over the configured procedure. */
  SQL_SERVER
}
