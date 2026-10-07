package io.codehunters.totp;

/** Number of digits in a generated TOTP code. */
public enum TotpDigits {
  /** Four-digit code. */
  FOUR(4),
  /** Six-digit code (most common). */
  SIX(6),
  /** Eight-digit code. */
  EIGHT(8);

  private final int value;

  TotpDigits(int value) {
    this.value = value;
  }

  /**
   * The numeric digit count.
   *
   * @return the number of digits
   */
  public int value() {
    return value;
  }
}
