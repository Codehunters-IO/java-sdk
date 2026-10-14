package io.codehunters.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Messages of one origin still waiting to be published: PENDING or IN_FLIGHT.
 *
 * @param pending how many messages wait
 * @param oldestOccurredAt when the oldest waiting message was written; empty when none waits
 */
public record OutboxBacklog(long pending, Optional<Instant> oldestOccurredAt) {

  /** Rejects a negative count and a missing optional. */
  public OutboxBacklog {
    if (pending < 0) {
      throw new IllegalArgumentException("pending must not be negative");
    }
    Objects.requireNonNull(oldestOccurredAt, "oldestOccurredAt");
  }
}
