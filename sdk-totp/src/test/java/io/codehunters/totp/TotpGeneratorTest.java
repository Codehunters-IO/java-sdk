package io.codehunters.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpGeneratorTest {

  private static final byte[] SEED_20 = "12345678901234567890".getBytes();

  private static final byte[] SEED_32 = "12345678901234567890123456789012".getBytes();

  private static final byte[] SEED_64 =
      "1234567890123456789012345678901234567890123456789012345678901234".getBytes();

  @Test
  void should_generate_six_digit_code() {
    var generator = new TotpGenerator();
    var secret = TotpSecret.fromBytes(SEED_20);
    var code = generator.generate(secret, Instant.ofEpochSecond(59));

    assertEquals(6, code.length());
  }

  @Test
  void should_generate_eight_digit_code() {
    var config =
        new TotpConfig(Duration.ofSeconds(30), TotpDigits.EIGHT, TotpAlgorithm.HMAC_SHA512);
    var generator = new TotpGenerator(config);
    var secret = TotpSecret.fromBytes(SEED_64);
    var code = generator.generate(secret, Instant.ofEpochSecond(59));

    assertEquals(8, code.length());
  }

  @Test
  void should_generate_four_digit_code() {
    var config = new TotpConfig(Duration.ofSeconds(30), TotpDigits.FOUR, TotpAlgorithm.HMAC_SHA512);
    var generator = new TotpGenerator(config);
    var secret = TotpSecret.fromBytes(SEED_64);
    var code = generator.generate(secret, Instant.ofEpochSecond(59));

    assertEquals(4, code.length());
  }

  @Test
  void should_generate_deterministic_code_for_same_time() {
    var generator = new TotpGenerator();
    var secret = TotpSecret.fromBytes(SEED_20);
    var time = Instant.ofEpochSecond(1234567890);

    var code1 = generator.generate(secret, time);
    var code2 = generator.generate(secret, time);

    assertEquals(code1, code2);
  }

  @Test
  void should_generate_different_codes_for_different_intervals() {
    var generator = new TotpGenerator();
    var secret = TotpSecret.fromBytes(SEED_20);

    var code1 = generator.generate(secret, Instant.ofEpochSecond(0));
    var code2 = generator.generate(secret, Instant.ofEpochSecond(60));

    assertFalse(code1.equals(code2));
  }

  @Test
  void should_generate_same_code_within_same_interval() {
    var generator = new TotpGenerator();
    var secret = TotpSecret.fromBytes(SEED_20);

    var code1 = generator.generate(secret, Instant.ofEpochSecond(0));
    var code2 = generator.generate(secret, Instant.ofEpochSecond(29));

    assertEquals(code1, code2);
  }

  @Test
  void should_verify_valid_code() {
    var generator = new TotpGenerator();
    var secret = TotpSecret.fromBytes(SEED_20);
    var time = Instant.ofEpochSecond(59);
    var code = generator.generate(secret, time);

    assertTrue(generator.verify(secret, code, 0, time));
  }

  @Test
  void should_reject_invalid_code() {
    var generator = new TotpGenerator();
    var secret = TotpSecret.fromBytes(SEED_20);
    var time = Instant.ofEpochSecond(59);

    assertFalse(generator.verify(secret, "000000", 0, time));
  }

  @Test
  void should_accept_code_within_variance_window() {
    var generator = new TotpGenerator();
    var secret = TotpSecret.fromBytes(SEED_20);
    var time = Instant.ofEpochSecond(59);
    var code = generator.generate(secret, time);

    assertTrue(generator.verify(secret, code, 1, time.plusSeconds(30)));
  }

  @Test
  void should_reject_code_outside_variance_window() {
    var generator = new TotpGenerator();
    var secret = TotpSecret.fromBytes(SEED_20);
    var time = Instant.ofEpochSecond(59);
    var code = generator.generate(secret, time);

    assertFalse(generator.verify(secret, code, 0, time.plusSeconds(60)));
  }

  @Test
  void should_work_with_sha1_algorithm() {
    var config = new TotpConfig(Duration.ofSeconds(30), TotpDigits.SIX, TotpAlgorithm.HMAC_SHA1);
    var generator = new TotpGenerator(config);
    var secret = TotpSecret.fromBytes(SEED_20);
    var code = generator.generate(secret, Instant.ofEpochSecond(59));

    assertEquals(6, code.length());
    assertTrue(generator.verify(secret, code, 0, Instant.ofEpochSecond(59)));
  }

  @Test
  void should_work_with_sha256_algorithm() {
    var config = new TotpConfig(Duration.ofSeconds(30), TotpDigits.SIX, TotpAlgorithm.HMAC_SHA256);
    var generator = new TotpGenerator(config);
    var secret = TotpSecret.fromBytes(SEED_32);
    var code = generator.generate(secret, Instant.ofEpochSecond(59));

    assertEquals(6, code.length());
    assertTrue(generator.verify(secret, code, 0, Instant.ofEpochSecond(59)));
  }

  @Test
  void should_work_with_base32_secret() {
    var generator = new TotpGenerator();
    var secret = TotpSecret.generate();
    var base32 = secret.toBase32();
    var restored = TotpSecret.fromBase32(base32);
    var time = Instant.ofEpochSecond(1234567890);

    var code1 = generator.generate(secret, time);
    var code2 = generator.generate(restored, time);

    assertEquals(code1, code2);
  }

  @Test
  void should_verify_with_default_variance() {
    var generator = new TotpGenerator();
    var secret = TotpSecret.fromBytes(SEED_20);
    var time = Instant.ofEpochSecond(59);
    var code = generator.generate(secret, time);

    assertTrue(generator.verify(secret, code, 1, time.plusSeconds(30)));
  }

  @Test
  void should_generate_and_verify_with_current_time_overloads() {
    var generator = new TotpGenerator();
    var secret = TotpSecret.fromBytes(SEED_20);

    var code = generator.generate(secret);

    assertEquals(6, code.length());
    assertTrue(generator.verify(secret, code));
  }

  @Test
  void should_verify_with_explicit_variance_and_current_time() {
    var generator = new TotpGenerator();
    var secret = TotpSecret.fromBytes(SEED_20);
    var code = generator.generate(secret);

    assertTrue(generator.verify(secret, code, 1));
  }
}
