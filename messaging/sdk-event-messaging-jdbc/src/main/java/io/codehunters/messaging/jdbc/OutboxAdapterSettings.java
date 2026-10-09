package io.codehunters.messaging.jdbc;

import java.time.Clock;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * Groups the scalar settings of {@link OutboxJdbcAdapter} so its constructor stays at three
 * parameters.
 *
 * @param origin the default origin stamped on messages that do not carry one; never blank
 * @param dialect the claim dialect for the outbox database
 * @param clock the clock for every timestamp the adapter writes (UTC expected)
 */
public record OutboxAdapterSettings(String origin, OutboxDialect dialect, Clock clock) {

  /** Validates the settings. */
  public OutboxAdapterSettings {
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(dialect, "dialect");
    Objects.requireNonNull(clock, "clock");
    if (origin.isBlank()) {
      throw new IllegalArgumentException("origin must not be blank");
    }
  }

  /**
   * Builds settings with the dialect auto-detected from the database and a UTC system clock.
   *
   * @param dataSource the outbox data source, read once to detect the dialect
   * @param origin the default origin
   * @return the settings
   */
  public static OutboxAdapterSettings defaults(DataSource dataSource, String origin) {
    return new OutboxAdapterSettings(
        origin, OutboxDialectResolver.resolve(dataSource, "auto"), Clock.systemUTC());
  }
}
