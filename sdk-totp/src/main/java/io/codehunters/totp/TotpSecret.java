package io.codehunters.totp;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable holder for a TOTP shared secret. Defensive copies are made on construction and access,
 * and {@link #toString()} never reveals the key material.
 */
public final class TotpSecret {

  private static final int DEFAULT_KEY_LENGTH = 20;

  private final byte[] key;

  private TotpSecret(byte[] key) {
    if (key == null || key.length == 0) {
      throw new TotpException("Secret key must not be null or empty");
    }
    this.key = Arrays.copyOf(key, key.length);
  }

  /**
   * Creates a secret from raw key bytes.
   *
   * @param key the raw secret key; must be non-null and non-empty
   * @return the secret
   * @throws TotpException if {@code key} is null or empty
   */
  public static TotpSecret fromBytes(byte[] key) {
    return new TotpSecret(key);
  }

  /**
   * Creates a secret from a Base32-encoded string.
   *
   * @param encoded the Base32-encoded secret
   * @return the secret
   */
  public static TotpSecret fromBase32(String encoded) {
    Objects.requireNonNull(encoded, "Base32 encoded secret must not be null");
    return new TotpSecret(Base32.decode(encoded));
  }

  /**
   * Generates a random secret of the default length (20 bytes).
   *
   * @return a randomly generated secret
   */
  public static TotpSecret generate() {
    return generate(DEFAULT_KEY_LENGTH);
  }

  /**
   * Generates a random secret of the given length.
   *
   * @param keyLength the key length in bytes
   * @return a randomly generated secret
   */
  public static TotpSecret generate(int keyLength) {
    byte[] key = new byte[keyLength];
    new SecureRandom().nextBytes(key);
    return new TotpSecret(key);
  }

  /**
   * Returns a defensive copy of the raw key bytes.
   *
   * @return a copy of the secret key
   */
  public byte[] key() {
    return Arrays.copyOf(key, key.length);
  }

  /**
   * Encodes the secret as a Base32 string.
   *
   * @return the Base32-encoded secret
   */
  public String toBase32() {
    return Base32.encode(key);
  }

  @Override
  public String toString() {
    return "TotpSecret[***]";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TotpSecret that)) return false;
    return Arrays.equals(key, that.key);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(key);
  }
}
