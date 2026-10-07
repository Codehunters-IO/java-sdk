package io.codehunters.jpa.auditing;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import org.springframework.data.auditing.DateTimeProvider;

/**
 * Audit timestamps read from a {@link Clock}, cut to microseconds: PostgreSQL keeps exactly that,
 * and SQL Server's {@code datetime2} and {@code datetimeoffset} keep 100 ns, so an entity holds the
 * same instant before and after a round trip on both.
 */
public final class ClockDateTimeProvider implements DateTimeProvider {

  private final Clock clock;

  /**
   * Creates a provider reading the given clock.
   *
   * @param clock the source of the current time
   * @throws IllegalArgumentException if {@code clock} is null
   */
  public ClockDateTimeProvider(Clock clock) {
    if (clock == null) {
      throw new IllegalArgumentException("clock must not be null");
    }
    this.clock = clock;
  }

  @Override
  public Optional<TemporalAccessor> getNow() {
    return Optional.of(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
  }
}
