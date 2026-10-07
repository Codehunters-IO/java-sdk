package io.codehunters.totp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class Base32Test {

  @Test
  void should_decode_rfc4648_test_vector_f() {
    assertArrayEquals("f".getBytes(), Base32.decode("MY======"));
  }

  @Test
  void should_decode_rfc4648_test_vector_fo() {
    assertArrayEquals("fo".getBytes(), Base32.decode("MZXQ===="));
  }

  @Test
  void should_decode_rfc4648_test_vector_foo() {
    assertArrayEquals("foo".getBytes(), Base32.decode("MZXW6==="));
  }

  @Test
  void should_decode_rfc4648_test_vector_foob() {
    assertArrayEquals("foob".getBytes(), Base32.decode("MZXW6YQ="));
  }

  @Test
  void should_decode_rfc4648_test_vector_fooba() {
    assertArrayEquals("fooba".getBytes(), Base32.decode("MZXW6YTB"));
  }

  @Test
  void should_decode_rfc4648_test_vector_foobar() {
    assertArrayEquals("foobar".getBytes(), Base32.decode("MZXW6YTBOI======"));
  }

  @Test
  void should_encode_rfc4648_test_vectors() {
    assertEquals("MY======", Base32.encode("f".getBytes()));
    assertEquals("MZXQ====", Base32.encode("fo".getBytes()));
    assertEquals("MZXW6===", Base32.encode("foo".getBytes()));
    assertEquals("MZXW6YQ=", Base32.encode("foob".getBytes()));
    assertEquals("MZXW6YTB", Base32.encode("fooba".getBytes()));
    assertEquals("MZXW6YTBOI======", Base32.encode("foobar".getBytes()));
  }

  @Test
  void should_decode_without_padding() {
    assertArrayEquals("foo".getBytes(), Base32.decode("MZXW6"));
  }

  @Test
  void should_decode_lowercase() {
    assertArrayEquals("foo".getBytes(), Base32.decode("mzxw6"));
  }

  @Test
  void should_throw_on_invalid_character() {
    assertThrows(TotpException.class, () -> Base32.decode("1INVALID"));
  }

  @Test
  void should_round_trip_arbitrary_bytes() {
    byte[] original = {0x00, (byte) 0xFF, 0x7F, (byte) 0x80, 0x01};
    byte[] decoded = Base32.decode(Base32.encode(original));

    assertArrayEquals(original, decoded);
  }
}
