package io.codehunters.security.authz.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Creates a value once and keeps it. After a failed creation it rethrows that failure without
 * retrying until the back-off has elapsed, so an unreachable Keycloak is not asked again on every
 * request.
 */
final class BackoffSupplier<T> implements Supplier<T> {

  private final Supplier<T> factory;
  private final Duration backoff;
  private final Clock clock;
  private T value;
  private RuntimeException lastFailure;
  private Instant retryAt = Instant.MIN;

  BackoffSupplier(Supplier<T> factory, Duration backoff, Clock clock) {
    this.factory = factory;
    this.backoff = backoff;
    this.clock = clock;
  }

  @Override
  public synchronized T get() {
    if (value != null) {
      return value;
    }
    if (lastFailure != null && clock.instant().isBefore(retryAt)) {
      throw lastFailure;
    }
    return create();
  }

  private T create() {
    try {
      value = factory.get();
      lastFailure = null;
      return value;
    } catch (RuntimeException e) {
      lastFailure = e;
      retryAt = clock.instant().plus(backoff);
      throw e;
    }
  }
}
