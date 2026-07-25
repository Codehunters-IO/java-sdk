package io.codehunters.totp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TotpSecretTest {

  @Test
  void should_create_from_bytes() {
    byte[] key = {0x01, 0x02, 0x03};
    var secret = TotpSecret.fromBytes(key);

    assertArrayEquals(key, secret.key());
  }

  @Test
  void should_defensive_copy_on_creation() {
    byte[] key = {0x01, 0x02, 0x03};
    var secret = TotpSecret.fromBytes(key);
    key[0] = 0x00;

    assertEquals((byte) 0x01, secret.key()[0]);
  }

  @Test
  void should_defensive_copy_on_access() {
    var secret = TotpSecret.fromBytes(new byte[] {0x01, 0x02, 0x03});
    byte[] key1 = secret.key();
    byte[] key2 = secret.key();

    assertNotSame(key1, key2);
  }

  @Test
  void should_create_from_base32() {
    var secret = TotpSecret.fromBase32("JBSWY3DPEE======");

    assertArrayEquals("Hello!".getBytes(), secret.key());
  }

  @Test
  void should_round_trip_base32() {
    byte[] original = {0x48, 0x65, 0x6C, 0x6C, 0x6F};
    var secret = TotpSecret.fromBytes(original);
    var restored = TotpSecret.fromBase32(secret.toBase32());

    assertArrayEquals(original, restored.key());
  }

  @Test
  void should_not_leak_secret_in_toString() {
    var secret = TotpSecret.fromBytes(new byte[] {0x01, 0x02});

    assertEquals("TotpSecret[***]", secret.toString());
  }

  @Test
  void should_throw_when_key_is_null() {
    assertThrows(TotpException.class, () -> TotpSecret.fromBytes(null));
  }

  @Test
  void should_throw_when_key_is_empty() {
    assertThrows(TotpException.class, () -> TotpSecret.fromBytes(new byte[0]));
  }

  @Test
  void should_throw_when_base32_is_null() {
    assertThrows(NullPointerException.class, () -> TotpSecret.fromBase32(null));
  }

  @Test
  void should_generate_random_secret() {
    var secret = TotpSecret.generate();

    assertEquals(20, secret.key().length);
  }

  @Test
  void should_generate_random_secret_with_custom_length() {
    var secret = TotpSecret.generate(32);

    assertEquals(32, secret.key().length);
  }

  @Test
  void should_be_equal_when_keys_match() {
    var a = TotpSecret.fromBytes(new byte[] {1, 2, 3});
    var b = TotpSecret.fromBytes(new byte[] {1, 2, 3});

    assertEquals(a, a);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void should_not_be_equal_to_different_key_or_type() {
    var a = TotpSecret.fromBytes(new byte[] {1, 2, 3});
    var other = TotpSecret.fromBytes(new byte[] {9, 9, 9});

    assertNotEquals(a, other);
    assertNotEquals(a, "not-a-secret");
  }
}
