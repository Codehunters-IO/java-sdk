package io.codehunters.geo;

/** How much of the map a scope covers. */
public enum Coverage {
  /** Every point is allowed; no zone is looked up. */
  UNRESTRICTED,

  /** Only points inside one of the scope's zones are allowed. */
  RESTRICTED
}
