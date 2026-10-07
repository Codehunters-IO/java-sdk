package io.codehunters.geo.config;

import io.codehunters.geo.sqlserver.SqlServerProcedure;
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
 */
@ConfigurationProperties(prefix = "codehunters.geo")
public record GeoProperties(
    @DefaultValue("true") boolean enabled, GeoAdapter adapter, @DefaultValue SqlServer sqlServer) {

  /** Requires a valid procedure when the {@code sql-server} adapter is selected. */
  public GeoProperties {
    if (adapter == GeoAdapter.SQL_SERVER && sqlServer.name() == null) {
      throw new IllegalArgumentException(
          "codehunters.geo.sql-server.procedure is required when"
              + " codehunters.geo.adapter=sql-server");
    }
  }

  /**
   * Settings of the {@code sql-server} adapter. See {@link SqlServerProcedure} for the procedure's
   * contract.
   *
   * @param name the procedure, {@code name} or {@code schema.name}; bound from {@code procedure}
   * @param parameters the procedure's parameter names, without {@code @}
   */
  public record SqlServer(@Name("procedure") String name, @DefaultValue Parameters parameters) {

    /** Validates the names; messages never repeat them. */
    public SqlServer {
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
}
