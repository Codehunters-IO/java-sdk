package io.codehunters.commons.time;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Coarse distance between two instants, for "3 days ago" style labels. The amount is floored, so
 * 119 seconds is one minute. Months are 30 days, capped at 11 so that 360 to 364 days still reads
 * "11 months", and years are 365 days.
 *
 * <p>Render it with {@link #toEnglish()}, or localize it from {@link #amount()}, {@link #unit()}
 * and {@link #direction()}.
 *
 * @param amount how many units, zero for {@link Unit#NOW}
 * @param unit the unit of the amount
 * @param direction whether the instant lies before or after the reference
 */
public record RelativeTime(long amount, Unit unit, Direction direction) {

  /** Granularity of a {@link RelativeTime}. */
  public enum Unit {
    /** Less than a minute away. */
    NOW,
    /** Less than an hour away. */
    MINUTES,
    /** Less than a day away. */
    HOURS,
    /** Less than a week away. */
    DAYS,
    /** Less than 30 days away. */
    WEEKS,
    /** Less than 365 days away; the amount is at most 11. */
    MONTHS,
    /** 365 days or more away. */
    YEARS
  }

  /** Whether an instant lies before or after the reference instant. */
  public enum Direction {
    /** Before the reference instant (or equal to it). */
    PAST,
    /** After the reference instant. */
    FUTURE
  }

  private static final long DAYS_PER_WEEK = 7;
  private static final long DAYS_PER_MONTH = 30;
  private static final long DAYS_PER_YEAR = 365;
  private static final long MAX_MONTHS = 11;

  /**
   * Validates the components.
   *
   * @param amount the amount, zero for {@link Unit#NOW} and positive otherwise
   * @param unit the unit, not null
   * @param direction the direction, not null
   */
  public RelativeTime {
    if (amount < 0 || unit == null || direction == null) {
      throw new IllegalArgumentException(
          "amount must not be negative; unit and direction are required");
    }
    if ((amount == 0) != (unit == Unit.NOW)) {
      throw new IllegalArgumentException("amount must be zero exactly when unit is NOW");
    }
  }

  /**
   * Measures how far {@code instant} is from {@code reference}.
   *
   * @param instant the instant to describe
   * @param reference usually the current time
   * @return the coarse distance
   */
  public static RelativeTime between(Instant instant, Instant reference) {
    if (instant == null || reference == null) {
      throw new IllegalArgumentException("instant and reference must not be null");
    }
    Direction direction = instant.isAfter(reference) ? Direction.FUTURE : Direction.PAST;
    Duration distance = Duration.between(instant, reference).abs();
    return of(distance, direction);
  }

  private static RelativeTime of(Duration distance, Direction direction) {
    long days = distance.toDays();
    if (days >= DAYS_PER_YEAR) {
      return new RelativeTime(days / DAYS_PER_YEAR, Unit.YEARS, direction);
    }
    if (days >= DAYS_PER_MONTH) {
      return new RelativeTime(Math.min(days / DAYS_PER_MONTH, MAX_MONTHS), Unit.MONTHS, direction);
    }
    if (days >= DAYS_PER_WEEK) {
      return new RelativeTime(days / DAYS_PER_WEEK, Unit.WEEKS, direction);
    }
    return ofLessThanAWeek(distance, direction);
  }

  private static RelativeTime ofLessThanAWeek(Duration distance, Direction direction) {
    if (distance.toDays() > 0) {
      return new RelativeTime(distance.toDays(), Unit.DAYS, direction);
    }
    if (distance.toHours() > 0) {
      return new RelativeTime(distance.toHours(), Unit.HOURS, direction);
    }
    if (distance.toMinutes() > 0) {
      return new RelativeTime(distance.toMinutes(), Unit.MINUTES, direction);
    }
    return new RelativeTime(0, Unit.NOW, direction);
  }

  /**
   * Renders the distance in English: {@code "just now"}, {@code "1 minute ago"}, {@code "in 2
   * hours"}.
   *
   * @return the English label
   */
  public String toEnglish() {
    if (unit == Unit.NOW) {
      return "just now";
    }
    String unitName = unit.name().toLowerCase(Locale.ROOT);
    String quantity =
        amount + " " + (amount == 1 ? unitName.substring(0, unitName.length() - 1) : unitName);
    return direction == Direction.PAST ? quantity + " ago" : "in " + quantity;
  }
}
