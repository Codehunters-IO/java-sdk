package io.codehunters.messaging.jdbc;

import io.codehunters.messaging.OutboxDispatchTrigger;
import java.time.Clock;
import java.util.Objects;

/**
 * Groups the scalar settings of {@link OutboxJdbcAdapter} so its constructor stays at three
 * parameters.
 *
 * @param origin the default origin stamped on messages that do not carry one; never blank
 * @param clock the clock for every timestamp the adapter writes (UTC expected)
 * @param trigger asked for a dispatch after each transaction that saved outbox messages commits, or
 *     right after a save made outside a transaction; {@link OutboxDispatchTrigger#none()} leaves
 *     dispatch to the poll
 */
public record OutboxAdapterSettings(String origin, Clock clock, OutboxDispatchTrigger trigger) {

  /** Validates the settings. */
  public OutboxAdapterSettings {
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(trigger, "trigger");
    if (origin.isBlank()) {
      throw new IllegalArgumentException("origin must not be blank");
    }
  }

  /**
   * Builds settings with a UTC system clock and no commit-time dispatch.
   *
   * @param origin the default origin
   * @return the settings
   */
  public static OutboxAdapterSettings defaults(String origin) {
    return new OutboxAdapterSettings(origin, Clock.systemUTC(), OutboxDispatchTrigger.none());
  }
}
