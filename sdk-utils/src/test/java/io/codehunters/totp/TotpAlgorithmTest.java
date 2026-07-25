package io.codehunters.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TotpAlgorithmTest {

  @Test
  void should_return_hmac_sha1_jce_name() {
    assertEquals("HmacSHA1", TotpAlgorithm.HMAC_SHA1.jceName());
  }

  @Test
  void should_return_hmac_sha256_jce_name() {
    assertEquals("HmacSHA256", TotpAlgorithm.HMAC_SHA256.jceName());
  }

  @Test
  void should_return_hmac_sha512_jce_name() {
    assertEquals("HmacSHA512", TotpAlgorithm.HMAC_SHA512.jceName());
  }

  @Test
  void should_have_three_algorithms() {
    assertEquals(3, TotpAlgorithm.values().length);
  }
}
