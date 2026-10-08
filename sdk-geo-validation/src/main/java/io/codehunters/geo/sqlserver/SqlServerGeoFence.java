package io.codehunters.geo.sqlserver;

import io.codehunters.geo.DenialReason;
import io.codehunters.geo.GeoDecision;
import io.codehunters.geo.GeoFenceValidator;
import io.codehunters.geo.GeoPoint;
import io.codehunters.geo.MatchedZone;
import io.codehunters.geo.ZoneRef;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Asks a stored procedure whether a point is inside the scope's zones. The procedure's OUTPUT
 * parameters are read back through one {@code SELECT}; {@code SET NOCOUNT ON} keeps the procedure's
 * own row counts from reaching the driver before that row. Thread-safe.
 */
public final class SqlServerGeoFence implements GeoFenceValidator {

  private final JdbcClient jdbc;
  private final String statement;

  /**
   * Creates the fence.
   *
   * @param jdbc client on the SQL Server database that holds the procedure
   * @param procedure the procedure and its parameter names
   */
  public SqlServerGeoFence(JdbcClient jdbc, SqlServerProcedure procedure) {
    if (jdbc == null || procedure == null) {
      throw new IllegalArgumentException("A SQL Server geofence needs a client and a procedure");
    }
    this.jdbc = jdbc;
    this.statement = statement(procedure);
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

  static String statement(SqlServerProcedure procedure) {
    SqlServerProcedure.Parameters p = procedure.parameters();
    List<String> outputs = new ArrayList<>();
    outputs.add(output(p.allowed(), "@r_allowed"));
    outputs.add(output(p.zoneId(), "@r_zone_id"));
    addOptional(outputs, p.zoneType(), "@r_zone_type");
    addOptional(outputs, p.zoneName(), "@r_zone_name");
    addOptional(outputs, p.zoneParent(), "@r_zone_parent");
    return "SET NOCOUNT ON;\n"
        + "DECLARE @r_allowed BIT, @r_zone_id NVARCHAR(128), @r_zone_type NVARCHAR(200),"
        + " @r_zone_name NVARCHAR(200), @r_zone_parent NVARCHAR(200);\n"
        + "EXEC "
        + quoted(procedure.name())
        + " @"
        + p.scope()
        + " = :scope, @"
        + p.latitude()
        + " = :lat, @"
        + p.longitude()
        + " = :lon, "
        + String.join(", ", outputs)
        + ";\n"
        + "SELECT @r_allowed AS allowed, @r_zone_id AS zone_id, @r_zone_type AS zone_type,"
        + " @r_zone_name AS zone_name, @r_zone_parent AS zone_parent;";
  }

  /** Brackets each part; the parts are validated identifiers, so they hold no {@code ]}. */
  private static String quoted(String name) {
    return "[" + name.replace(".", "].[") + "]";
  }

  private static String output(String parameter, String variable) {
    return "@" + parameter + " = " + variable + " OUTPUT";
  }

  private static void addOptional(List<String> outputs, String parameter, String variable) {
    if (!parameter.isEmpty()) {
      outputs.add(output(parameter, variable));
    }
  }

  private static GeoDecision decision(ResultSet rs) throws SQLException {
    boolean allowed = rs.getBoolean("allowed");
    if (rs.wasNull()) {
      return GeoDecision.denied(DenialReason.ZONE_NOT_FOUND);
    }
    String zoneId = rs.getString("zone_id");
    if (!allowed || zoneId == null || zoneId.isBlank()) {
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
