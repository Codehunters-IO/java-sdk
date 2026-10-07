package io.codehunters.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TotpDigitsTest {

  @Test
  @SuppressWarnings("deprecation")
  void should_return_4_for_FOUR() {
    assertEquals(4, TotpDigits.FOUR.value());
  }

  @Test
  void should_return_6_for_SIX() {
    assertEquals(6, TotpDigits.SIX.value());
  }

  @Test
  void should_return_8_for_EIGHT() {
    assertEquals(8, TotpDigits.EIGHT.value());
  }

  @Test
  void should_have_three_values() {
    assertEquals(3, TotpDigits.values().length);
  }
}
