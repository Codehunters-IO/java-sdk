package io.codehunters.commons.totp;

/** Number of digits in a generated TOTP code. */
public enum TotpDigits {
  /**
   * Four-digit code.
   *
   * @deprecated below the six-digit minimum of RFC 4226; with the largest variance one guess in
   *     about 476 is accepted. Use {@link #SIX} or {@link #EIGHT}.
   */
  @Deprecated(since = "2.0.0")
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
