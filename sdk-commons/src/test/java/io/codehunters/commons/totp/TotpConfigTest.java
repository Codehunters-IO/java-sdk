package io.codehunters.commons.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TotpConfigTest {

  @Test
  void should_use_authenticator_app_settings_when_default_config_is_requested() {
    var config = TotpConfig.defaultConfig();

    assertEquals(Duration.ofSeconds(30), config.timeStep());
    assertEquals(TotpDigits.SIX, config.digits());
    assertEquals(TotpAlgorithm.HMAC_SHA1, config.algorithm());
  }

  @Test
  void should_create_custom_config() {
    var config =
        new TotpConfig(Duration.ofSeconds(60), TotpDigits.EIGHT, TotpAlgorithm.HMAC_SHA256);

    assertEquals(Duration.ofSeconds(60), config.timeStep());
    assertEquals(TotpDigits.EIGHT, config.digits());
    assertEquals(TotpAlgorithm.HMAC_SHA256, config.algorithm());
  }

  @Test
  void should_throw_when_time_step_is_null() {
    assertThrows(
        NullPointerException.class,
        () -> new TotpConfig(null, TotpDigits.SIX, TotpAlgorithm.HMAC_SHA512));
  }

  @Test
  void should_throw_when_time_step_is_zero() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TotpConfig(Duration.ZERO, TotpDigits.SIX, TotpAlgorithm.HMAC_SHA512));
  }

  @Test
  void should_throw_when_time_step_is_negative() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TotpConfig(Duration.ofSeconds(-1), TotpDigits.SIX, TotpAlgorithm.HMAC_SHA512));
  }

  @Test
  void should_throw_when_digits_is_null() {
    assertThrows(
        NullPointerException.class,
        () -> new TotpConfig(Duration.ofSeconds(30), null, TotpAlgorithm.HMAC_SHA512));
  }

  @Test
  void should_throw_when_algorithm_is_null() {
    assertThrows(
        NullPointerException.class,
        () -> new TotpConfig(Duration.ofSeconds(30), TotpDigits.SIX, null));
  }
}
