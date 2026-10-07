package io.codehunters.measure;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/**
 * Logs a warning the first time it is raised for a key, and at debug level afterwards, so a
 * misconfigured meter does not log on every call. Keys are meter names and method signatures, which
 * the application's code bounds.
 */
final class WarnOnce {

  private final Logger log;
  private final Set<String> warned = ConcurrentHashMap.newKeySet();

  WarnOnce(Logger log) {
    this.log = log;
  }

  void warn(String key, String message, Object... arguments) {
    if (warned.add(key)) {
      log.warn(message, arguments);
    } else {
      log.debug(message, arguments);
    }
  }
}
