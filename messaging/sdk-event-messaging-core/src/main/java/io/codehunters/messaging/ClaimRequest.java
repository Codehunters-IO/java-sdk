package io.codehunters.messaging;

import java.time.Duration;
import java.util.Objects;

/**
 * What one dispatch run claims from the {@link OutboxDispatchStore}.
 *
 * @param origin the origin of the rows to claim
 * @param claimedBy the identifier of the claiming dispatcher; the store derives a unique claim
 *     marker from it
 * @param batchSize the maximum number of rows claimed; at least 1
 * @param claimTtl the time after which an in-flight claim is stale and reclaimable; positive
 */
public record ClaimRequest(String origin, String claimedBy, int batchSize, Duration claimTtl) {

  /** Validates the request. */
  public ClaimRequest {
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(claimedBy, "claimedBy");
    Objects.requireNonNull(claimTtl, "claimTtl");
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be at least 1");
    }
    if (claimTtl.isNegative() || claimTtl.isZero()) {
      throw new IllegalArgumentException("claimTtl must be positive");
    }
  }
}
