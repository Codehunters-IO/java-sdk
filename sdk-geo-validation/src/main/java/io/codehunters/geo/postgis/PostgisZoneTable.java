package io.codehunters.geo.postgis;

import java.util.regex.Pattern;

/**
 * The table or view that holds the zones, and the names of its columns. Every name is validated as
 * an identifier because it is written into the statement, where it is double-quoted: names are
 * matched case-sensitively, as PostgreSQL stores them (lower case for unquoted DDL).
 *
 * <p>Each row is one zone of one scope:
 *
 * <ul>
 *   <li>{@code scope}: the scope reference, compared with {@code ZoneRef.value()};
 *   <li>{@code zone-id}: the zone's id, read as text; when several zones cover the point, the
 *       lowest id (in the column's own order) wins;
 *   <li>{@code geometry}: a {@code geography} or a {@code geometry} in SRID 4326 (polygon or
 *       multipolygon);
 *   <li>optionally {@code zone-type}, {@code zone-name} and {@code zone-parent}, free text reported
 *       back as-is.
 * </ul>
 *
 * @param name the table or view, {@code name} or {@code schema.name}
 * @param columns the column names
 */
public record PostgisZoneTable(String name, Columns columns) {

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,62}");
  private static final Pattern QUALIFIED =
      Pattern.compile(IDENTIFIER.pattern() + "(\\." + IDENTIFIER.pattern() + ")?");

  /**
   * Validates the name.
   *
   * @throws IllegalArgumentException if the name is not an identifier; the message does not repeat
   *     it
   */
  public PostgisZoneTable {
    if (name == null || !QUALIFIED.matcher(name).matches()) {
      throw new IllegalArgumentException(
          "The zone table name must be an identifier, optionally schema-qualified");
    }
    columns = columns == null ? Columns.DEFAULTS : columns;
  }

  /**
   * A table using the default column names.
   *
   * @param name the table or view, {@code name} or {@code schema.name}
   * @return the table
   */
  public static PostgisZoneTable named(String name) {
    return new PostgisZoneTable(name, Columns.DEFAULTS);
  }

  /**
   * Column names. The optional ones may be blank, in which case the matched zone reports no such
   * attribute.
   *
   * @param scope the scope reference
   * @param zoneId the zone's id
   * @param geometry the zone's area
   * @param zoneType optional: the zone's kind
   * @param zoneName optional: the zone's name
   * @param zoneParent optional: the zone the matched one belongs to
   */
  public record Columns(
      String scope,
      String zoneId,
      String geometry,
      String zoneType,
      String zoneName,
      String zoneParent) {

    /** {@code scope_ref, zone_id, geom, zone_type, zone_name, zone_parent}. */
    public static final Columns DEFAULTS =
        new Columns("scope_ref", "zone_id", "geom", "zone_type", "zone_name", "zone_parent");

    /**
     * Validates the names and blanks out absent optional columns.
     *
     * @throws IllegalArgumentException if a required name is missing or any name is not an
     *     identifier
     */
    public Columns {
      zoneType = optional(zoneType);
      zoneName = optional(zoneName);
      zoneParent = optional(zoneParent);
      boolean valid =
          identifiers(scope, zoneId, geometry)
              && optionalIdentifiers(zoneType, zoneName, zoneParent);
      if (!valid) {
        throw new IllegalArgumentException(
            "Zone table column names must be identifiers: scope, zone-id and geometry are"
                + " required");
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
