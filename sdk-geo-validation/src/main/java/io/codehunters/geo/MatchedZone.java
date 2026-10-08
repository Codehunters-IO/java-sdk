package io.codehunters.geo;

import java.util.Optional;

/**
 * The zone a point fell inside. Every attribute but the id is free text owned by the zone source:
 * the SDK never maps a type to an enum, so a source can introduce new zone types without breaking
 * callers.
 *
 * @param id the zone's id, not blank
 * @param type the zone's kind, e.g. {@code CITY}, if the source reports one
 * @param name the zone's display name, if the source reports one
 * @param parent the zone the matched one belongs to, e.g. a region, if the source reports one
 */
public record MatchedZone(
    String id, Optional<String> type, Optional<String> name, Optional<String> parent) {

  /**
   * Validates the id and normalises the optional attributes.
   *
   * @throws IllegalArgumentException if the id is blank
   */
  public MatchedZone {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("Zone id must not be blank");
    }
    type = present(type);
    name = present(name);
    parent = present(parent);
  }

  /**
   * Builds a zone from nullable attributes; {@code null} or blank ones are left empty.
   *
   * @param id the zone's id, not blank
   * @param type the zone's kind, or {@code null}
   * @param name the zone's display name, or {@code null}
   * @param parent the enclosing zone, or {@code null}
   * @return the zone
   */
  public static MatchedZone of(String id, String type, String name, String parent) {
    return new MatchedZone(
        id, Optional.ofNullable(type), Optional.ofNullable(name), Optional.ofNullable(parent));
  }

  private static Optional<String> present(Optional<String> value) {
    return value == null ? Optional.empty() : value.filter(text -> !text.isBlank());
  }
}
