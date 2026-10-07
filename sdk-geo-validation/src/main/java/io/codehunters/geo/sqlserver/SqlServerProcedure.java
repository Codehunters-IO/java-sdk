package io.codehunters.geo.sqlserver;

import java.util.regex.Pattern;

/**
 * The stored procedure that answers a geofence check, and the names of its parameters. Every name
 * is validated as an identifier because it is written into the statement.
 *
 * <p>The procedure receives the scope reference ({@code NVARCHAR(128)}), the latitude and the
 * longitude ({@code FLOAT}), and sets these OUTPUT parameters:
 *
 * <ul>
 *   <li>{@code allowed} ({@code BIT}): {@code 1} inside a zone, {@code 0} outside every zone,
 *       {@code NULL} when the scope has no zones;
 *   <li>{@code zone-id} ({@code NVARCHAR(128)}): the matched zone; an allowed point without one is
 *       treated as denied;
 *   <li>optionally {@code zone-type}, {@code zone-name} and {@code zone-parent} ({@code
 *       NVARCHAR(200)}), free text reported back as-is.
 * </ul>
 *
 * @param name the procedure, {@code name} or {@code schema.name}
 * @param parameters the procedure's parameter names
 */
public record SqlServerProcedure(String name, Parameters parameters) {

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,127}");
  private static final Pattern QUALIFIED =
      Pattern.compile(IDENTIFIER.pattern() + "(\\." + IDENTIFIER.pattern() + ")?");

  /**
   * Validates the name.
   *
   * @throws IllegalArgumentException if the name is not an identifier; the message does not repeat
   *     it
   */
  public SqlServerProcedure {
    if (name == null || !QUALIFIED.matcher(name).matches()) {
      throw new IllegalArgumentException(
          "The procedure name must be an identifier, optionally schema-qualified");
    }
    parameters = parameters == null ? Parameters.DEFAULTS : parameters;
  }

  /**
   * A procedure using the default parameter names.
   *
   * @param name the procedure, {@code name} or {@code schema.name}
   * @return the procedure
   */
  public static SqlServerProcedure named(String name) {
    return new SqlServerProcedure(name, Parameters.DEFAULTS);
  }

  /**
   * Parameter names, without the leading {@code @}. The optional outputs may be blank, in which
   * case they are not passed and the matched zone reports no such attribute.
   *
   * @param scope input: the scope reference
   * @param latitude input: the latitude
   * @param longitude input: the longitude
   * @param allowed output: the decision flag
   * @param zoneId output: the matched zone's id
   * @param zoneType optional output: the matched zone's kind
   * @param zoneName optional output: the matched zone's name
   * @param zoneParent optional output: the zone the matched one belongs to
   */
  public record Parameters(
      String scope,
      String latitude,
      String longitude,
      String allowed,
      String zoneId,
      String zoneType,
      String zoneName,
      String zoneParent) {

    /** {@code scope_ref, lat, lon, allowed, zone_id, zone_type, zone_name, zone_parent}. */
    public static final Parameters DEFAULTS =
        new Parameters(
            "scope_ref",
            "lat",
            "lon",
            "allowed",
            "zone_id",
            "zone_type",
            "zone_name",
            "zone_parent");

    /**
     * Validates the names and blanks out absent optional outputs.
     *
     * @throws IllegalArgumentException if a required name is missing or any name is not an
     *     identifier
     */
    public Parameters {
      zoneType = optional(zoneType);
      zoneName = optional(zoneName);
      zoneParent = optional(zoneParent);
      boolean valid =
          identifiers(scope, latitude, longitude, allowed, zoneId)
              && optionalIdentifiers(zoneType, zoneName, zoneParent);
      if (!valid) {
        throw new IllegalArgumentException(
            "Procedure parameter names must be identifiers without '@': scope, latitude,"
                + " longitude, allowed and zone-id are required");
      }
    }

    private static String optional(String name) {
      return name == null || name.isBlank() ? "" : name;
    }

    private static boolean identifiers(String... names) {
      for (String name : names) {
        if (name == null || !IDENTIFIER.matcher(name).matches()) {
          return false;
        }
      }
      return true;
    }

    private static boolean optionalIdentifiers(String... names) {
      for (String name : names) {
        if (!name.isEmpty() && !IDENTIFIER.matcher(name).matches()) {
          return false;
        }
      }
      return true;
    }
  }
}
