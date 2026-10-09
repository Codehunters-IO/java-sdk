package io.codehunters.commons.totp;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Immutable holder for a TOTP shared secret. Defensive copies are made on construction and access,
 * and {@link #toString()} never reveals the key material.
 *
 * <p>Generated secrets are at least {@value #MIN_KEY_LENGTH} bytes. Imported secrets ({@link
 * #fromBytes}, {@link #fromBase32}, {@link #fromHex}) only have to be non-empty, so secrets already
 * enrolled with a shorter key keep working.
 *
 * <p>Two secrets are equal when their keys are; the comparison takes constant time. {@link
 * #hashCode()} is derived from the key, so never log it.
 */
public final class TotpSecret {

  /** The shortest key {@link #generate(int)} creates, in bytes (128 bits, RFC 4226 R6). */
  public static final int MIN_KEY_LENGTH = 16;

  private static final SecureRandom RANDOM = new SecureRandom();

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
   * Creates a secret from a hexadecimal string, as used by the RFC 6238 test vectors. Upper and
   * lower case digits are accepted.
   *
   * @param hex the hex-encoded secret; an even number of hex digits
   * @return the secret
   * @throws TotpException if {@code hex} is not valid hex; the message never repeats the input
   */
  public static TotpSecret fromHex(String hex) {
    Objects.requireNonNull(hex, "hex encoded secret must not be null");
    byte[] key;
    try {
      key = HexFormat.of().parseHex(hex);
    } catch (IllegalArgumentException e) {
      throw new TotpException("Invalid hex secret");
    }
    return new TotpSecret(key);
  }

  /**
   * Generates a random secret for the default algorithm, HMAC-SHA1: 20 bytes. Use {@link
   * #generate(TotpAlgorithm)} for another algorithm.
   *
   * @return a randomly generated secret
   */
  public static TotpSecret generate() {
    return generate(TotpConfig.defaultConfig().algorithm());
  }

  /**
   * Generates a random secret as long as the algorithm's hash.
   *
   * @param algorithm the HMAC algorithm the secret is for
   * @return a randomly generated secret
   */
  public static TotpSecret generate(TotpAlgorithm algorithm) {
    return generate(algorithm.keyLength());
  }

  /**
   * Generates a random secret of the given length.
   *
   * @param keyLength the key length in bytes, at least {@value #MIN_KEY_LENGTH}
   * @return a randomly generated secret
   * @throws TotpException if {@code keyLength} is below {@value #MIN_KEY_LENGTH}
   */
  public static TotpSecret generate(int keyLength) {
    if (keyLength < MIN_KEY_LENGTH) {
      throw new TotpException("Generated keys must be at least " + MIN_KEY_LENGTH + " bytes");
    }
    byte[] key = new byte[keyLength];
    RANDOM.nextBytes(key);
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
    return MessageDigest.isEqual(key, that.key);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(key);
  }
}
