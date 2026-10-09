package io.codehunters.geo.config;

import io.codehunters.geo.postgis.PostgisZoneTable;
import io.codehunters.geo.sqlserver.SqlServerProcedure;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

/**
 * Configuration for the geofence SDK, bound from the {@code codehunters.geo} prefix. Invalid values
 * fail at startup.
 *
 * @param enabled whether the SDK auto-configuration is active
 * @param adapter the geofence to register; none when unset, so an application can supply its own
 * @param sqlServer settings of the {@code sql-server} adapter
 * @param postgis settings of the {@code postgis} adapter
 */
@ConfigurationProperties(prefix = "codehunters.geo")
public record GeoProperties(
    @DefaultValue("true") boolean enabled,
    GeoAdapter adapter,
    @DefaultValue SqlServer sqlServer,
    @DefaultValue Postgis postgis) {

  /** Requires the selected JDBC adapter's procedure or table. */
  public GeoProperties {
    if (adapter == GeoAdapter.SQL_SERVER && sqlServer.name() == null) {
      throw new IllegalArgumentException(
          "codehunters.geo.sql-server.procedure is required when"
              + " codehunters.geo.adapter=sql-server");
    }
    if (adapter == GeoAdapter.POSTGIS && postgis.name() == null) {
      throw new IllegalArgumentException(
          "codehunters.geo.postgis.table is required when codehunters.geo.adapter=postgis");
    }
  }

  /**
   * Settings of the {@code sql-server} adapter. See {@link SqlServerProcedure} for the procedure's
   * contract.
   *
   * @param name the procedure, {@code name} or {@code schema.name}; bound from {@code procedure}
   * @param parameters the procedure's parameter names, without {@code @}
   * @param queryTimeout the longest the procedure may run, rounded up to whole seconds; positive
   */
  public record SqlServer(
      @Name("procedure") String name,
      @DefaultValue Parameters parameters,
      @DefaultValue("2s") Duration queryTimeout) {

    /** Validates the names and the timeout; messages never repeat the names. */
    public SqlServer {
      if (queryTimeout == null || queryTimeout.isNegative() || queryTimeout.isZero()) {
        throw new IllegalArgumentException(
            "codehunters.geo.sql-server.query-timeout must be positive");
      }
      name = name == null || name.isBlank() ? null : name;
      if (name != null) {
        try {
          new SqlServerProcedure(name, parameters.toProcedureParameters());
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("codehunters.geo.sql-server: " + e.getMessage());
        }
      }
    }

    /**
     * The configured procedure.
     *
     * @return the procedure
     * @throws IllegalStateException if no procedure is configured
     */
    public SqlServerProcedure procedure() {
      if (name == null) {
        throw new IllegalStateException("codehunters.geo.sql-server.procedure is not set");
      }
      return new SqlServerProcedure(name, parameters.toProcedureParameters());
    }
  }

  /**
   * Parameter names of the procedure. Blank optional outputs are not passed.
   *
   * @param scope input: the scope reference
   * @param latitude input: the latitude
   * @param longitude input: the longitude
   * @param allowed output: the decision flag
   * @param zoneId output: the matched zone's id
   * @param zoneType optional output: the zone's kind
   * @param zoneName optional output: the zone's name
   * @param zoneParent optional output: the enclosing zone
   */
  public record Parameters(
      @DefaultValue("scope_ref") String scope,
      @DefaultValue("lat") String latitude,
      @DefaultValue("lon") String longitude,
      @DefaultValue("allowed") String allowed,
      @DefaultValue("zone_id") String zoneId,
      @DefaultValue("zone_type") String zoneType,
      @DefaultValue("zone_name") String zoneName,
      @DefaultValue("zone_parent") String zoneParent) {

    SqlServerProcedure.Parameters toProcedureParameters() {
      return new SqlServerProcedure.Parameters(
          scope, latitude, longitude, allowed, zoneId, zoneType, zoneName, zoneParent);
    }
  }

  /**
   * Settings of the {@code postgis} adapter. See {@link PostgisZoneTable} for the table's contract.
   *
   * @param name the table or view, {@code name} or {@code schema.name}; bound from {@code table}
   * @param columns the table's column names
   * @param queryTimeout the longest the query may run, rounded up to whole seconds; positive
   */
  public record Postgis(
      @Name("table") String name,
      @DefaultValue Columns columns,
      @DefaultValue("2s") Duration queryTimeout) {

    /** Validates the names and the timeout; messages never repeat the names. */
    public Postgis {
      if (queryTimeout == null || queryTimeout.isNegative() || queryTimeout.isZero()) {
        throw new IllegalArgumentException(
            "codehunters.geo.postgis.query-timeout must be positive");
      }
      name = name == null || name.isBlank() ? null : name;
      if (name != null) {
        try {
          new PostgisZoneTable(name, columns.toTableColumns());
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("codehunters.geo.postgis: " + e.getMessage());
        }
      }
    }

    /**
     * The configured zone table.
     *
     * @return the table
     * @throws IllegalStateException if no table is configured
     */
    public PostgisZoneTable table() {
      if (name == null) {
        throw new IllegalStateException("codehunters.geo.postgis.table is not set");
      }
      return new PostgisZoneTable(name, columns.toTableColumns());
    }
  }

  /**
   * Column names of the zone table. Blank optional columns are not read.
   *
   * @param scope the scope reference
   * @param zoneId the zone's id
   * @param geometry the zone's area, {@code geography} or {@code geometry} in SRID 4326
   * @param zoneType optional: the zone's kind
   * @param zoneName optional: the zone's name
   * @param zoneParent optional: the enclosing zone
   */
  public record Columns(
      @DefaultValue("scope_ref") String scope,
      @DefaultValue("zone_id") String zoneId,
      @DefaultValue("geom") String geometry,
      @DefaultValue("zone_type") String zoneType,
      @DefaultValue("zone_name") String zoneName,
      @DefaultValue("zone_parent") String zoneParent) {

    PostgisZoneTable.Columns toTableColumns() {
      return new PostgisZoneTable.Columns(scope, zoneId, geometry, zoneType, zoneName, zoneParent);
    }
  }
}
