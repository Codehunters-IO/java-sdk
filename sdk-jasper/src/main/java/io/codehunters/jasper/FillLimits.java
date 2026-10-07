package io.codehunters.jasper;

import java.time.Duration;

/**
 * How far a fill may go before it is stopped. Both limits run on JasperReports' governors while the
 * report is filled, so they also bound {@link ReportRenderer#render}, which has no output limit.
 * Templates are trusted code and can raise them through their own governor properties.
 *
 * @param maxPages the most pages a report may fill, 1 to 1000000
 * @param timeout the longest a fill may run, 1 ms to 1 hour
 */
public record FillLimits(int maxPages, Duration timeout) {

  // Declared before DEFAULT: the constructor reads them while DEFAULT is built.
  private static final int MAX_PAGES = 1_000_000;
  private static final Duration MIN_TIMEOUT = Duration.ofMillis(1);
  private static final Duration MAX_TIMEOUT = Duration.ofHours(1);

  /** 2000 pages and two minutes. */
  public static final FillLimits DEFAULT = new FillLimits(2_000, Duration.ofMinutes(2));

  /** Validates the limits. */
  public FillLimits {
    if (maxPages < 1 || maxPages > MAX_PAGES) {
      throw new IllegalArgumentException("max-pages must be between 1 and " + MAX_PAGES);
    }
    if (timeout == null
        || timeout.compareTo(MIN_TIMEOUT) < 0
        || timeout.compareTo(MAX_TIMEOUT) > 0) {
      throw new IllegalArgumentException("timeout must be between 1 ms and 1 hour");
    }
  }
}
