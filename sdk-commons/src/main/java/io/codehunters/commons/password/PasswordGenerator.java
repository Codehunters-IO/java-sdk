package io.codehunters.commons.password;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/**
 * Generates random passwords and numeric codes. Every password contains at least one lower-case
 * letter, upper-case letter, digit and symbol by construction, so it always satisfies {@link
 * PasswordPolicy#DEFAULT} at the default length. Characters that are easy to confuse ({@code i l o
 * 0 1 I L O}) are never used.
 *
 * <p>Instances are thread-safe when their random generator is; the default one is.
 */
public final class PasswordGenerator {

  /** Shortest password {@link #generate(int)} creates. */
  public static final int MIN_LENGTH = 12;

  /** Length of {@link #generate()}. */
  public static final int DEFAULT_LENGTH = 16;

  /** Longest password or code this generator creates. */
  public static final int MAX_LENGTH = 128;

  /** Shortest code {@link #numeric(int)} creates. */
  public static final int MIN_NUMERIC_LENGTH = 4;

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final String LOWER = "abcdefghjkmnpqrstuvwxyz";
  private static final String UPPER = "ABCDEFGHJKMNPQRSTUVWXYZ";
  private static final String DIGITS = "23456789";
  private static final String SYMBOLS = "+*-_$@#.=&";
  private static final String[] CLASSES = {LOWER, UPPER, DIGITS, SYMBOLS};
  private static final String ALL = LOWER + UPPER + DIGITS + SYMBOLS;
  private static final String NUMERIC = "0123456789";

  private final RandomGenerator random;

  /** Creates a generator backed by a shared {@link SecureRandom}. */
  public PasswordGenerator() {
    this(SECURE_RANDOM);
  }

  /**
   * Creates a generator backed by the given source of randomness. Use a cryptographically strong
   * one for real passwords.
   *
   * @param random the source of randomness
   */
  public PasswordGenerator(RandomGenerator random) {
    if (random == null) {
      throw new IllegalArgumentException("random must not be null");
    }
    this.random = random;
  }

  /**
   * Generates a password of {@link #DEFAULT_LENGTH} characters.
   *
   * @return the password
   */
  public String generate() {
    return generate(DEFAULT_LENGTH);
  }

  /**
   * Generates a password of the given length.
   *
   * @param length between {@link #MIN_LENGTH} and {@link #MAX_LENGTH}
   * @return the password
   * @throws IllegalArgumentException if the length is out of bounds
   */
  public String generate(int length) {
    requireLength(length, MIN_LENGTH);
    char[] password = new char[length];
    for (int i = 0; i < length; i++) {
      password[i] = pick(i < CLASSES.length ? CLASSES[i] : ALL);
    }
    shuffle(password);
    return new String(password);
  }

  /**
   * Generates a numeric code, such as a PIN, of the given length. Any digit may appear.
   *
   * @param length between {@link #MIN_NUMERIC_LENGTH} and {@link #MAX_LENGTH}
   * @return the code
   * @throws IllegalArgumentException if the length is out of bounds
   */
  public String numeric(int length) {
    requireLength(length, MIN_NUMERIC_LENGTH);
    char[] code = new char[length];
    for (int i = 0; i < length; i++) {
      code[i] = pick(NUMERIC);
    }
    return new String(code);
  }

  private char pick(String alphabet) {
    return alphabet.charAt(random.nextInt(alphabet.length()));
  }

  private void shuffle(char[] chars) {
    for (int i = chars.length - 1; i > 0; i--) {
      int j = random.nextInt(i + 1);
      char swap = chars[i];
      chars[i] = chars[j];
      chars[j] = swap;
    }
  }

  private static void requireLength(int length, int min) {
    if (length < min || length > MAX_LENGTH) {
      throw new IllegalArgumentException("length must be between " + min + " and " + MAX_LENGTH);
    }
  }
}
