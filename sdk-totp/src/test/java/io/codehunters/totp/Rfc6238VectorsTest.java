package io.codehunters.totp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Test vectors from RFC 6238, Appendix B. */
class Rfc6238VectorsTest {

  private static final String SEED_SHA1 = "3132333435363738393031323334353637383930";
  private static final String SEED_SHA256 =
      "3132333435363738393031323334353637383930313233343536373839303132";
  private static final String SEED_SHA512 =
      "3132333435363738393031323334353637383930313233343536373839303132"
          + "3334353637383930313233343536373839303132333435363738393031323334";

  @ParameterizedTest(name = "{0} at {1}")
  @CsvSource({
    "HMAC_SHA1, 59, 94287082",
    "HMAC_SHA1, 1111111109, 07081804",
    "HMAC_SHA1, 1111111111, 14050471",
    "HMAC_SHA1, 1234567890, 89005924",
    "HMAC_SHA1, 2000000000, 69279037",
    "HMAC_SHA1, 20000000000, 65353130",
    "HMAC_SHA256, 59, 46119246",
    "HMAC_SHA256, 1111111109, 68084774",
    "HMAC_SHA256, 1111111111, 67062674",
    "HMAC_SHA256, 1234567890, 91819424",
    "HMAC_SHA256, 2000000000, 90698825",
    "HMAC_SHA256, 20000000000, 77737706",
    "HMAC_SHA512, 59, 90693936",
    "HMAC_SHA512, 1111111109, 25091201",
    "HMAC_SHA512, 1111111111, 99943326",
    "HMAC_SHA512, 1234567890, 93441116",
    "HMAC_SHA512, 2000000000, 38618901",
    "HMAC_SHA512, 20000000000, 47863826"
  })
  void should_match_rfc_6238_vector_when_generating(
      TotpAlgorithm algorithm, long epochSecond, String expected) {
    var generator = generatorFor(algorithm);

    assertThat(generator.generate(seedFor(algorithm), Instant.ofEpochSecond(epochSecond)))
        .isEqualTo(expected);
  }

  @ParameterizedTest(name = "{0} at {1}")
  @CsvSource({
    "HMAC_SHA1, 1111111109, 07081804",
    "HMAC_SHA256, 1234567890, 91819424",
    "HMAC_SHA512, 20000000000, 47863826"
  })
  void should_verify_rfc_6238_vector_when_variance_is_zero(
      TotpAlgorithm algorithm, long epochSecond, String code) {
    var generator = generatorFor(algorithm);

    assertThat(generator.verify(seedFor(algorithm), code, 0, Instant.ofEpochSecond(epochSecond)))
        .isTrue();
  }

  private static TotpGenerator generatorFor(TotpAlgorithm algorithm) {
    return new TotpGenerator(new TotpConfig(Duration.ofSeconds(30), TotpDigits.EIGHT, algorithm));
  }

  private static TotpSecret seedFor(TotpAlgorithm algorithm) {
    return switch (algorithm) {
      case HMAC_SHA1 -> TotpSecret.fromHex(SEED_SHA1);
      case HMAC_SHA256 -> TotpSecret.fromHex(SEED_SHA256);
      case HMAC_SHA512 -> TotpSecret.fromHex(SEED_SHA512);
    };
  }
}
