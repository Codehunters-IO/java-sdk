package io.codehunters.geo;

import java.util.UUID;

/** Outbound port: decides whether a coordinate authorizes a raffle purchase. */
public interface RaffleGeoValidationPort {
  /**
   * Validates whether a point is authorized to purchase a raffle ticket.
   *
   * @param raffleId the raffle identifier
   * @param point the geographic coordinate to validate
   * @param coverageType the raffle's territorial footprint; {@code NATIONAL} authorizes the whole
   *     country without a polygon check
   * @return a {@link GeoValidationDecision} indicating allow/deny and the matched zone
   */
  GeoValidationDecision canPurchase(UUID raffleId, GeoPoint point, CoverageType coverageType);
}
