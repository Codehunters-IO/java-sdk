package io.codehunters.measure;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caps the distinct values each expression tag can take on a meter. Once a tag has reached the
 * limit, new values are recorded as {@value #OTHER}, so the meter keeps counting every call while
 * the number of time series stays bounded. Values already seen keep their own series.
 */
final class TagCardinalityLimiter {

  static final String OTHER = "other";

  private static final Logger log = LoggerFactory.getLogger(TagCardinalityLimiter.class);

  private final int maxValues;
  private final Map<String, Set<String>> seen = new ConcurrentHashMap<>();
  private final Set<String> overflowing = ConcurrentHashMap.newKeySet();

  TagCardinalityLimiter(int maxValues) {
    if (maxValues < 1) {
      throw new IllegalArgumentException("maxValues must be at least 1");
    }
    this.maxValues = maxValues;
  }

  String limit(String meterName, String key, String value) {
    String series = meterName + '\u0000' + key;
    Set<String> values = seen.computeIfAbsent(series, k -> ConcurrentHashMap.newKeySet());
    if (values.contains(value)) {
      return value;
    }
    if (values.size() >= maxValues) {
      warnOnce(series, meterName, key);
      return OTHER;
    }
    values.add(value);
    return value;
  }

  private void warnOnce(String series, String meterName, String key) {
    if (overflowing.add(series)) {
      log.warn(
          "Tag '{}' of meter '{}' reached {} distinct values; further values are recorded as '{}'",
          key,
          meterName,
          maxValues,
          OTHER);
    }
  }
}
