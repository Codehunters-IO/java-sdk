package io.codehunters.measure.internal;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caps the distinct values an expression tag may take in one series, a series being one meter name
 * at one method and one tag key. Values beyond the cap are recorded as {@value #OTHER}, so a tag
 * fed by user input cannot create unbounded series. The cap is reached once and logged once per
 * series; known values keep their own series. The total number of values a name can take is
 * therefore the cap times the number of methods sharing it.
 */
final class TagCardinalityLimiter {

  static final String OTHER = "other";

  private static final Logger log = LoggerFactory.getLogger(TagCardinalityLimiter.class);

  private final int maxValues;
  private final Map<String, Series> series = new ConcurrentHashMap<>();

  TagCardinalityLimiter(int maxValues) {
    if (maxValues < 1) {
      throw new IllegalArgumentException("maxValues must be at least 1");
    }
    this.maxValues = maxValues;
  }

  /**
   * Returns the value to record.
   *
   * @param site the meter name and method the tag belongs to
   * @param key the tag key
   * @param value the evaluated value
   * @return {@code value}, or {@value #OTHER} once the series is full
   */
  String limit(String site, String key, String value) {
    Series values = series.computeIfAbsent(site + '\u0000' + key, id -> new Series());
    if (values.admit(value, maxValues)) {
      return value;
    }
    warnOnce(values, site, key);
    return OTHER;
  }

  private void warnOnce(Series values, String site, String key) {
    if (values.markOverflowing()) {
      log.warn(
          "Tag '{}' of {} reached {} distinct values; further values are recorded as '{}'",
          key,
          site,
          maxValues,
          OTHER);
    }
  }

  /** The values one series has admitted. */
  private static final class Series {

    private final Set<String> values = ConcurrentHashMap.newKeySet();
    private boolean overflowing;

    boolean admit(String value, int maxValues) {
      if (values.contains(value)) {
        return true;
      }
      synchronized (this) {
        if (values.contains(value) || values.size() < maxValues) {
          values.add(value);
          return true;
        }
        return false;
      }
    }

    synchronized boolean markOverflowing() {
      boolean first = !overflowing;
      overflowing = true;
      return first;
    }
  }
}
