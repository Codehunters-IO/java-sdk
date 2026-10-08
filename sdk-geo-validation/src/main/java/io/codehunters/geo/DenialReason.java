package io.codehunters.geo;

/** Why a point was refused. */
public enum DenialReason {
  /** The scope has zones, and none of them contains the point. */
  OUTSIDE_ZONE,

  /** The scope has no zones at all, so nothing can be allowed. */
  ZONE_NOT_FOUND
}
