package io.codehunters.geo;

/**
 * A raffle's territorial footprint, owned by the management service. Drives the geo gate: {@link
 * #NATIONAL} authorizes the whole country without a polygon check; {@link #DEPARTMENTAL} and {@link
 * #LOCAL} require the point to fall inside an allowed department or city polygon.
 */
public enum CoverageType {
  NATIONAL,
  DEPARTMENTAL,
  LOCAL
}
