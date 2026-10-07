package io.codehunters.commons.time;

import java.time.LocalDateTime;

/** Builds cron expressions that the JDK has no helper for. */
public final class CronExpressions {

  private static final int MIN_YEAR = 1970;
  private static final int MAX_YEAR = 2099;

  private CronExpressions() {}

  /**
   * Builds a Quartz cron expression ({@code sec min hour day month ? year}) that fires once, at
   * {@code at}. The scheduler evaluates it in its own time zone, so convert {@code at} to that zone
   * first. Fractions of a second are ignored. Spring's {@code CronExpression} has no year field and
   * cannot express a single firing.
   *
   * @param at the wall-clock time of the single firing, in a year from 1970 to 2099
   * @return the Quartz expression
   * @throws IllegalArgumentException if {@code at} is null or outside the years Quartz accepts
   */
  public static String quartzAt(LocalDateTime at) {
    if (at == null) {
      throw new IllegalArgumentException("at must not be null");
    }
    if (at.getYear() < MIN_YEAR || at.getYear() > MAX_YEAR) {
      throw new IllegalArgumentException("Quartz only schedules years 1970 to 2099");
    }
    return "%d %d %d %d %d ? %d"
        .formatted(
            at.getSecond(),
            at.getMinute(),
            at.getHour(),
            at.getDayOfMonth(),
            at.getMonthValue(),
            at.getYear());
  }
}
