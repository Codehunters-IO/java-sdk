package io.codehunters.geo;

import java.util.Optional;

/**
 * Outcome of a geofence check: allowed (with the matched zone, or none when the scope is
 * unrestricted) or denied with a reason.
 *
 * @param allowed whether the point is allowed
 * @param zone the matched zone; empty when denied or unrestricted
 * @param denialReason why the point was refused; empty when allowed
 */
public record GeoDecision(
    boolean allowed, Optional<MatchedZone> zone, Optional<DenialReason> denialReason) {

  /**
   * Checks the decision is consistent.
   *
   * @throws IllegalArgumentException if a denial has no reason or a zone, or an allowance has a
   *     reason
   */
  public GeoDecision {
    zone = zone == null ? Optional.empty() : zone;
    denialReason = denialReason == null ? Optional.empty() : denialReason;
    if (!allowed && (denialReason.isEmpty() || zone.isPresent())) {
      throw new IllegalArgumentException("A denied decision needs a reason and no zone");
    }
    if (allowed && denialReason.isPresent()) {
      throw new IllegalArgumentException("An allowed decision has no reason");
    }
  }

  /**
   * Allows the point inside the given zone.
   *
   * @param zone the zone that contains the point
   * @return the decision
   */
  public static GeoDecision allowed(MatchedZone zone) {
    return new GeoDecision(true, Optional.of(zone), Optional.empty());
  }

  /**
   * Allows the point without a zone, because the scope covers every point.
   *
   * @return the decision
   */
  public static GeoDecision unrestricted() {
    return new GeoDecision(true, Optional.empty(), Optional.empty());
  }

  /**
   * Refuses the point.
   *
   * @param reason why
   * @return the decision
   */
  public static GeoDecision denied(DenialReason reason) {
    return new GeoDecision(false, Optional.empty(), Optional.of(reason));
  }
}
