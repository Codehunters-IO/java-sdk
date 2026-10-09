package io.codehunters.commons.totp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Generates and verifies RFC 6238 TOTP codes. Instances are immutable and thread-safe; a fresh
 * {@link javax.crypto.Mac} is created per call.
 *
 * <p>Verification compares every candidate in the window in constant time and does not stop at the
 * first match. {@link #verify} does not prevent a code from being reused inside its window. For
 * one-time use, store the step {@link #verifyAfter} returns per secret and pass it back on the next
 * attempt.
 */
public final class TotpGenerator {

  private static final int[] DIGITS_POWER = {
    1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000
  };

  /** The largest number of steps of drift {@link #verify} accepts on each side. */
  public static final int MAX_VARIANCE = 10;

  /**
   * The {@code lastAcceptedStep} to pass to {@link #verifyAfter} when no code has been accepted for
   * the secret yet.
   */
  public static final long NO_ACCEPTED_STEP = Long.MIN_VALUE;

  private final TotpConfig config;
  private final Clock clock;

  /** Creates a generator using {@link TotpConfig#defaultConfig()}. */
  public TotpGenerator() {
    this(TotpConfig.defaultConfig());
  }

  /**
   * Creates a generator with the given configuration, reading the time from the UTC system clock.
   *
   * @param config the TOTP parameters
   */
  public TotpGenerator(TotpConfig config) {
    this(config, Clock.systemUTC());
  }

  /**
   * Creates a generator that reads the current time from the given clock.
   *
   * @param config the TOTP parameters
   * @param clock the source of the current time
   */
  public TotpGenerator(TotpConfig config, Clock clock) {
    this.config = Objects.requireNonNull(config, "config must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * Generates the code for the current time.
   *
   * @param secret the shared secret
   * @return the TOTP code
   */
  public String generate(TotpSecret secret) {
    return generate(secret, clock.instant());
  }

  /**
   * Generates the code for the given instant.
   *
   * @param secret the shared secret
   * @param at the instant to compute the code for
   * @return the TOTP code
   */
  public String generate(TotpSecret secret, Instant at) {
    long counter = computeCounter(at);
    return generateCode(secret, counter);
  }

  /**
   * Verifies a code against the current time, allowing one step of drift on each side.
   *
   * @param secret the shared secret
   * @param code the code to verify
   * @return {@code true} if the code is valid
   */
  public boolean verify(TotpSecret secret, String code) {
    return verify(secret, code, 1);
  }

  /**
   * Verifies a code against the current time with the given drift tolerance.
   *
   * @param secret the shared secret
   * @param code the code to verify
   * @param variance the number of steps of drift tolerated on each side, {@code 0} to {@value
   *     #MAX_VARIANCE}
   * @return {@code true} if the code is valid
   * @throws IllegalArgumentException if {@code variance} is outside {@code 0..}{@value
   *     #MAX_VARIANCE}
   */
  public boolean verify(TotpSecret secret, String code, int variance) {
    return verify(secret, code, variance, clock.instant());
  }

  /**
   * Verifies a code against a given instant with the given drift tolerance.
   *
   * @param secret the shared secret
   * @param code the code to verify
   * @param variance the number of steps of drift tolerated on each side, {@code 0} to {@value
   *     #MAX_VARIANCE}
   * @param at the instant to verify against
   * @return {@code true} if the code is valid within the variance window; {@code false} for a
   *     {@code null} code
   * @throws IllegalArgumentException if {@code variance} is outside {@code 0..}{@value
   *     #MAX_VARIANCE}
   */
  public boolean verify(TotpSecret secret, String code, int variance, Instant at) {
    return verifyStep(secret, code, variance, at).isPresent();
  }

  /**
   * Verifies a code against the current time and returns the time step it matched.
   *
   * @param secret the shared secret
   * @param code the code to verify
   * @param variance the number of steps of drift tolerated on each side, {@code 0} to {@value
   *     #MAX_VARIANCE}
   * @return the matched time step, or empty if the code is invalid or {@code null}
   * @throws IllegalArgumentException if {@code variance} is outside {@code 0..}{@value
   *     #MAX_VARIANCE}
   */
  public OptionalLong verifyStep(TotpSecret secret, String code, int variance) {
    return verifyStep(secret, code, variance, clock.instant());
  }

  /**
   * Verifies a code against a given instant and returns the time step it matched. When the code
   * matches more than one step of the window, the latest one is returned.
   *
   * @param secret the shared secret
   * @param code the code to verify
   * @param variance the number of steps of drift tolerated on each side, {@code 0} to {@value
   *     #MAX_VARIANCE}
   * @param at the instant to verify against
   * @return the matched time step, or empty if the code is invalid or {@code null}
   * @throws IllegalArgumentException if {@code variance} is outside {@code 0..}{@value
   *     #MAX_VARIANCE}
   */
  public OptionalLong verifyStep(TotpSecret secret, String code, int variance, Instant at) {
    return match(secret, code, variance, new Window(computeCounter(at), NO_ACCEPTED_STEP));
  }

  /**
   * Verifies a code against the current time, accepting it only for a time step after {@code
   * lastAcceptedStep}. Store the returned step per secret and pass it back next time: a code that
   * was already accepted, or an older one, is then rejected.
   *
   * @param secret the shared secret
   * @param code the code to verify
   * @param variance the number of steps of drift tolerated on each side, {@code 0} to {@value
   *     #MAX_VARIANCE}
   * @param lastAcceptedStep the step returned by the last successful call for this secret, or
   *     {@link #NO_ACCEPTED_STEP}
   * @return the matched time step, or empty if the code is invalid, {@code null} or not after
   *     {@code lastAcceptedStep}
   * @throws IllegalArgumentException if {@code variance} is outside {@code 0..}{@value
   *     #MAX_VARIANCE}
   */
  public OptionalLong verifyAfter(
      TotpSecret secret, String code, int variance, long lastAcceptedStep) {
    var window = new Window(computeCounter(clock.instant()), lastAcceptedStep);
    return match(secret, code, variance, window);
  }

  private OptionalLong match(TotpSecret secret, String code, int variance, Window window) {
    if (variance < 0 || variance > MAX_VARIANCE) {
      throw new IllegalArgumentException("variance must be between 0 and " + MAX_VARIANCE);
    }
    if (code == null) {
      return OptionalLong.empty();
    }
    return latestMatch(secret, code.getBytes(StandardCharsets.US_ASCII), variance, window);
  }

  // Every candidate is computed and compared, whatever matched before, so timing does not reveal
  // which step (if any) the code belongs to.
  private OptionalLong latestMatch(TotpSecret secret, byte[] code, int variance, Window window) {
    OptionalLong matched = OptionalLong.empty();
    for (long step = window.current() - variance; step <= window.current() + variance; step++) {
      boolean equal = MessageDigest.isEqual(codeBytes(secret, step), code);
      matched = equal && step > window.lastAccepted() ? OptionalLong.of(step) : matched;
    }
    return matched;
  }

  private long computeCounter(Instant at) {
    return Math.floorDiv(at.getEpochSecond(), config.timeStep().getSeconds());
  }

  private byte[] codeBytes(TotpSecret secret, long counter) {
    return generateCode(secret, counter).getBytes(StandardCharsets.US_ASCII);
  }

  private String generateCode(TotpSecret secret, long counter) {
    byte[] hash = hmac(secret.key(), counter);
    int offset = hash[hash.length - 1] & 0x0F;
    int binary =
        ((hash[offset] & 0x7F) << 24)
            | ((hash[offset + 1] & 0xFF) << 16)
            | ((hash[offset + 2] & 0xFF) << 8)
            | (hash[offset + 3] & 0xFF);

    int codeDigits = config.digits().value();
    int otp = binary % DIGITS_POWER[codeDigits];
    return String.format(Locale.ROOT, "%0" + codeDigits + "d", otp);
  }

  private byte[] hmac(byte[] key, long counter) {
    try {
      Mac mac = Mac.getInstance(config.algorithm().jceName());
      mac.init(new SecretKeySpec(key, "RAW"));
      return mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
    } catch (GeneralSecurityException e) {
      throw new TotpException("Failed to compute HMAC", e);
    }
  }

  private record Window(long current, long lastAccepted) {}
}
