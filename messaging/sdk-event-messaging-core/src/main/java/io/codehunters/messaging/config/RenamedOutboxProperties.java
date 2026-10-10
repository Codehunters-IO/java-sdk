package io.codehunters.messaging.config;

import java.util.Map;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Fails startup when a timing key of the 8.x line is still set. Those keys were renamed to typed
 * durations in 9.0 and are otherwise silently ignored, so a tuned claim TTL or backoff would fall
 * back to the defaults unnoticed. Keys are matched in any relaxed form ({@code ttlMs}, {@code
 * CODEHUNTERS_OUTBOX_CLAIM_TTLMS}, ...).
 */
final class RenamedOutboxProperties {

  private static final Map<String, String> REPLACEMENTS =
      Map.of(
          "codehunters.outbox.claim.ttl-ms", "codehunters.outbox.claim.ttl",
          "codehunters.outbox.backoff.base-delay-ms", "codehunters.outbox.backoff.base-delay",
          "codehunters.outbox.backoff.max-delay-ms", "codehunters.outbox.backoff.max-delay");

  private static final String MESSAGE =
      "%s was renamed in sdk-event-messaging 9.0 and is no longer read; set %s as a duration"
          + " (e.g. 5m, 30s, 1h) instead";

  private RenamedOutboxProperties() {}

  /**
   * Rejects the environment when it sets a renamed key.
   *
   * @param environment the application environment
   * @throws IllegalStateException naming the first renamed key found and its replacement
   */
  static void reject(Environment environment) {
    Binder binder = Binder.get(environment);
    REPLACEMENTS.forEach(
        (oldKey, newKey) -> {
          if (binder.bind(oldKey, Bindable.of(String.class)).isBound()) {
            throw new IllegalStateException(MESSAGE.formatted(oldKey, newKey));
          }
        });
  }
}
