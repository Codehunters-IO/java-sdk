package io.codehunters.totp.config;

import io.codehunters.totp.TotpAlgorithm;
import io.codehunters.totp.TotpDigits;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the TOTP SDK, bound from the {@code codehunters.totp} prefix. Missing values
 * fall back to 30s step, six digits and HMAC-SHA512.
 *
 * @param timeStep the duration of one time window
 * @param digits the number of digits in generated codes
 * @param algorithm the HMAC algorithm
 */
@ConfigurationProperties(prefix = "codehunters.totp")
public record TotpProperties(Duration timeStep, TotpDigits digits, TotpAlgorithm algorithm) {

  /** Applies default values for any unset property. */
  public TotpProperties {
    if (timeStep == null) timeStep = Duration.ofSeconds(30);
    if (digits == null) digits = TotpDigits.SIX;
    if (algorithm == null) algorithm = TotpAlgorithm.HMAC_SHA512;
  }
}
