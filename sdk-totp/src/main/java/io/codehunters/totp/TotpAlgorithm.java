package io.codehunters.totp;

/** HMAC algorithm used to derive TOTP codes, mapped to its JCE {@code Mac} name. */
public enum TotpAlgorithm {
  /** HMAC-SHA1 ({@code HmacSHA1}). */
  HMAC_SHA1("HmacSHA1", 20),
  /** HMAC-SHA256 ({@code HmacSHA256}). */
  HMAC_SHA256("HmacSHA256", 32),
  /** HMAC-SHA512 ({@code HmacSHA512}). */
  HMAC_SHA512("HmacSHA512", 64);

  private final String jceName;
  private final int keyLength;

  TotpAlgorithm(String jceName, int keyLength) {
    this.jceName = jceName;
    this.keyLength = keyLength;
  }

  /**
   * The JCE algorithm name passed to {@link javax.crypto.Mac#getInstance(String)}.
   *
   * @return the JCE {@code Mac} algorithm name
   */
  public String jceName() {
    return jceName;
  }

  /**
   * The recommended secret length for this algorithm: the length of its hash output, as used by the
   * RFC 6238 Appendix B seeds.
   *
   * @return the key length in bytes
   */
  public int keyLength() {
    return keyLength;
  }
}
