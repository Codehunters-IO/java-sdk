package io.codehunters.totp;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Generates and verifies RFC 6238 TOTP codes. Instances are immutable and thread-safe; a fresh
 * {@link javax.crypto.Mac} is created per call.
 */
public final class TotpGenerator {

  private static final int[] DIGITS_POWER = {
    1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000
  };

  private final TotpConfig config;

  /** Creates a generator using {@link TotpConfig#defaultConfig()}. */
  public TotpGenerator() {
    this(TotpConfig.defaultConfig());
  }

  /**
   * Creates a generator with the given configuration.
   *
   * @param config the TOTP parameters
   */
  public TotpGenerator(TotpConfig config) {
    this.config = config;
  }

  /**
   * Generates the code for the current time.
   *
   * @param secret the shared secret
   * @return the TOTP code
   */
  public String generate(TotpSecret secret) {
    return generate(secret, Instant.now());
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
   * @param variance the number of steps of drift tolerated on each side
   * @return {@code true} if the code is valid
   */
  public boolean verify(TotpSecret secret, String code, int variance) {
    return verify(secret, code, variance, Instant.now());
  }

  /**
   * Verifies a code against a given instant with the given drift tolerance.
   *
   * @param secret the shared secret
   * @param code the code to verify
   * @param variance the number of steps of drift tolerated on each side
   * @param at the instant to verify against
   * @return {@code true} if the code is valid within the variance window
   */
  public boolean verify(TotpSecret secret, String code, int variance, Instant at) {
    long counter = computeCounter(at);
    int window = Math.max(variance, 0);
    for (int i = -window; i <= window; i++) {
      String candidate = generateCode(secret, counter + i);
      if (candidate.equals(code)) {
        return true;
      }
    }
    return false;
  }

  private long computeCounter(Instant at) {
    return at.getEpochSecond() / config.timeStep().toSeconds();
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
    return String.format("%0" + codeDigits + "d", otp);
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
}
