package io.codehunters.commons.totp.config;

import io.codehunters.commons.totp.TotpAlgorithm;
import io.codehunters.commons.totp.TotpConfig;
import io.codehunters.commons.totp.TotpDigits;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the TOTP SDK, bound from the {@code codehunters.totp} prefix. Missing values
 * fall back to {@link TotpConfig#defaultConfig()}: 30s step, six digits and HMAC-SHA1.
 *
 * @param timeStep the duration of one time window
 * @param digits the number of digits in generated codes
 * @param algorithm the HMAC algorithm
 */
@ConfigurationProperties(prefix = "codehunters.totp")
public record TotpProperties(Duration timeStep, TotpDigits digits, TotpAlgorithm algorithm) {

  private static final TotpConfig DEFAULTS = TotpConfig.defaultConfig();

  /** Applies default values for any unset property. */
  public TotpProperties {
    if (timeStep == null) timeStep = DEFAULTS.timeStep();
    if (digits == null) digits = DEFAULTS.digits();
    if (algorithm == null) algorithm = DEFAULTS.algorithm();
  }
}
