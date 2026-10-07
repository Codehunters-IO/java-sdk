package io.codehunters.totp;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable TOTP parameters: time step, code length and HMAC algorithm.
 *
 * @param timeStep the duration of one time window; a whole number of seconds, at least one
 * @param digits the number of digits in generated codes
 * @param algorithm the HMAC algorithm
 */
public record TotpConfig(Duration timeStep, TotpDigits digits, TotpAlgorithm algorithm) {

  /** The longest accepted time step. */
  public static final Duration MAX_TIME_STEP = Duration.ofMinutes(10);

  private static final Duration DEFAULT_TIME_STEP = Duration.ofSeconds(30);

  /** Validates the parameters. */
  public TotpConfig {
    Objects.requireNonNull(timeStep, "timeStep must not be null");
    Objects.requireNonNull(digits, "digits must not be null");
    Objects.requireNonNull(algorithm, "algorithm must not be null");
    if (timeStep.getSeconds() < 1 || timeStep.getNano() != 0) {
      throw new IllegalArgumentException("timeStep must be a whole number of seconds, at least 1s");
    }
  }

  /**
   * Default configuration: 30s step, six digits, HMAC-SHA512.
   *
   * @return a default {@link TotpConfig}
   */
  public static TotpConfig defaultConfig() {
    return new TotpConfig(DEFAULT_TIME_STEP, TotpDigits.SIX, TotpAlgorithm.HMAC_SHA512);
  }
}
