package io.codehunters.messaging.jdbc;

import java.time.Clock;
import java.util.Objects;

/**
 * Groups the scalar settings of {@link OutboxJdbcAdapter} so its constructor stays at three
 * parameters.
 *
 * @param origin the default origin stamped on messages that do not carry one; never blank
 * @param clock the clock for every timestamp the adapter writes (UTC expected)
 */
public record OutboxAdapterSettings(String origin, Clock clock) {

  /** Validates the settings. */
  public OutboxAdapterSettings {
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(clock, "clock");
    if (origin.isBlank()) {
      throw new IllegalArgumentException("origin must not be blank");
    }
  }

  /**
   * Builds settings with a UTC system clock.
   *
   * @param origin the default origin
   * @return the settings
   */
  public static OutboxAdapterSettings defaults(String origin) {
    return new OutboxAdapterSettings(origin, Clock.systemUTC());
  }
}
