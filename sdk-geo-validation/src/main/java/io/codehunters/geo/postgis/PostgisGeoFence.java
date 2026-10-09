package io.codehunters.geo.postgis;

import io.codehunters.geo.DenialReason;
import io.codehunters.geo.GeoDecision;
import io.codehunters.geo.GeoFenceValidator;
import io.codehunters.geo.GeoPoint;
import io.codehunters.geo.MatchedZone;
import io.codehunters.geo.ZoneRef;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Checks a point against the scope's zones in a PostGIS table, in one round trip that tells a scope
 * without zones from a point outside them. Coverage is tested on the geography ({@code ST_Covers}):
 * edges are great-circle arcs, a zone may cross the antimeridian, and a point on the border is
 * inside. Thread-safe.
 */
public final class PostgisGeoFence implements GeoFenceValidator {

  private final JdbcClient jdbc;
  private final String statement;

  /**
   * Creates the fence.
   *
   * @param jdbc client on the PostGIS database that holds the zones
   * @param table the zone table and its column names
   */
  public PostgisGeoFence(JdbcClient jdbc, PostgisZoneTable table) {
    if (jdbc == null || table == null) {
      throw new IllegalArgumentException("A PostGIS geofence needs a client and a zone table");
    }
    this.jdbc = jdbc;
    this.statement = statement(table);
  }

  @Override
  public GeoDecision validate(ZoneRef scope, GeoPoint point) {
    return jdbc.sql(statement)
        .param("scope", scope.value())
        .param("lat", point.latitude().value())
        .param("lon", point.longitude().value())
        .query((rs, row) -> decision(rs))
        .single();
  }

  static String statement(PostgisZoneTable table) {
    PostgisZoneTable.Columns c = table.columns();
    return "WITH scope_zones AS (SELECT "
        + quoted(c.zoneId())
        + " AS zone_id, "
        + optional(c.zoneType(), "zone_type")
        + ", "
        + optional(c.zoneName(), "zone_name")
        + ", "
        + optional(c.zoneParent(), "zone_parent")
        + ", "
        + quoted(c.geometry())
        + " AS geom FROM "
        + quoted(table.name())
        + " WHERE "
        + quoted(c.scope())
        + " = :scope)\n"
        + "SELECT EXISTS (SELECT 1 FROM scope_zones) AS has_zones,"
        + " m.zone_id, m.zone_type, m.zone_name, m.zone_parent\n"
        + "FROM (SELECT 1) AS one LEFT JOIN LATERAL (\n"
        + "  SELECT zone_id, zone_type, zone_name, zone_parent FROM scope_zones\n"
        + "  WHERE ST_Covers(geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography)\n"
        + "  ORDER BY zone_id LIMIT 1) AS m ON TRUE";
  }

  /** Double-quotes each part; the parts are validated identifiers, so they hold no {@code "}. */
  private static String quoted(String name) {
    return "\"" + name.replace(".", "\".\"") + "\"";
  }

  private static String optional(String column, String alias) {
    String value = column.isEmpty() ? "CAST(NULL AS text)" : quoted(column);
    return value + " AS " + alias;
  }

  private static GeoDecision decision(ResultSet rs) throws SQLException {
    if (!rs.getBoolean("has_zones")) {
      return GeoDecision.denied(DenialReason.ZONE_NOT_FOUND);
    }
    String zoneId = rs.getString("zone_id");
    if (zoneId == null || zoneId.isBlank()) {
      return GeoDecision.denied(DenialReason.OUTSIDE_ZONE);
    }
    return GeoDecision.allowed(
        MatchedZone.of(
            zoneId,
            rs.getString("zone_type"),
            rs.getString("zone_name"),
            rs.getString("zone_parent")));
  }
}
