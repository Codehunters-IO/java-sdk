package io.codehunters.messaging.dispatch;

import java.time.Duration;
import java.util.Objects;

/**
 * How often and how late a failed publish is retried: up to {@code maxAttempts} attempts, waiting
 * {@code baseDelay} after the first failure and twice as long after each further one, capped at
 * {@code maxDelay}.
 *
 * @param maxAttempts the attempts after which a message is marked FAILED; at least 1
 * @param baseDelay the wait after the first failure; positive
 * @param maxDelay the longest wait; positive
 */
public record RetryPolicy(int maxAttempts, Duration baseDelay, Duration maxDelay) {

  private static final int MAX_SHIFT = 30;

  /** Validates the policy. */
  public RetryPolicy {
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be at least 1");
    }
    requirePositive(baseDelay, "baseDelay");
    requirePositive(maxDelay, "maxDelay");
  }

  /**
   * Whether a message that was charged {@code attempts} attempts may not be tried again.
   *
   * @param attempts the attempts charged so far
   * @return {@code true} when {@code attempts} reached {@code maxAttempts}
   */
  public boolean isExhausted(int attempts) {
    return attempts >= maxAttempts;
  }

  /**
   * The wait before the next attempt of a message that failed {@code attempts} times.
   *
   * @param attempts the attempts charged so far, at least 1
   * @return {@code baseDelay * 2^(attempts - 1)}, capped at {@code maxDelay}
   */
  public Duration backoff(int attempts) {
    int shift = Math.min(Math.max(attempts - 1, 0), MAX_SHIFT);
    try {
      Duration delay = baseDelay.multipliedBy(1L << shift);
      return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    } catch (ArithmeticException e) {
      return maxDelay;
    }
  }

  private static void requirePositive(Duration value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isNegative() || value.isZero()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }
}
