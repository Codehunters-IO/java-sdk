package io.codehunters.messaging.domain.model;

/** Lifecycle status of an outbox row. */
public enum OutboxStatus {
  /** Message waiting to be claimed and published. */
  PENDING,
  /** Message has been claimed by a worker and is being published. */
  IN_FLIGHT,
  /** Message was successfully published to the broker. */
  PUBLISHED,
  /** Publication failed after exhausting all retry attempts. */
  FAILED
}
