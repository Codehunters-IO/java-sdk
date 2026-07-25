package io.codehunters.totp;

/** HMAC algorithm used to derive TOTP codes, mapped to its JCE {@code Mac} name. */
public enum TotpAlgorithm {
  /** HMAC-SHA1 ({@code HmacSHA1}). */
  HMAC_SHA1("HmacSHA1"),
  /** HMAC-SHA256 ({@code HmacSHA256}). */
  HMAC_SHA256("HmacSHA256"),
  /** HMAC-SHA512 ({@code HmacSHA512}). */
  HMAC_SHA512("HmacSHA512");

  private final String jceName;

  TotpAlgorithm(String jceName) {
    this.jceName = jceName;
  }

  /**
   * The JCE algorithm name passed to {@link javax.crypto.Mac#getInstance(String)}.
   *
   * @return the JCE {@code Mac} algorithm name
   */
  public String jceName() {
    return jceName;
  }
}
