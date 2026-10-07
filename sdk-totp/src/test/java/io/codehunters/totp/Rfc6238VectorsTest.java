package io.codehunters.totp;

import static io.codehunters.totp.TotpAlgorithm.HMAC_SHA1;
import static io.codehunters.totp.TotpAlgorithm.HMAC_SHA256;
import static io.codehunters.totp.TotpAlgorithm.HMAC_SHA512;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Test vectors from RFC 6238, Appendix B. */
class Rfc6238VectorsTest {

  private static final String SEED_SHA1 = "3132333435363738393031323334353637383930";
  private static final String SEED_SHA256 =
      "3132333435363738393031323334353637383930313233343536373839303132";
  private static final String SEED_SHA512 =
      "3132333435363738393031323334353637383930313233343536373839303132"
          + "3334353637383930313233343536373839303132333435363738393031323334";

  static Stream<Arguments> vectors() {
    return Stream.of(
        arguments(HMAC_SHA1, SEED_SHA1, 59L, "94287082"),
        arguments(HMAC_SHA1, SEED_SHA1, 1111111109L, "07081804"),
        arguments(HMAC_SHA1, SEED_SHA1, 1111111111L, "14050471"),
        arguments(HMAC_SHA1, SEED_SHA1, 1234567890L, "89005924"),
        arguments(HMAC_SHA1, SEED_SHA1, 2000000000L, "69279037"),
        arguments(HMAC_SHA1, SEED_SHA1, 20000000000L, "65353130"),
        arguments(HMAC_SHA256, SEED_SHA256, 59L, "46119246"),
        arguments(HMAC_SHA256, SEED_SHA256, 1111111109L, "68084774"),
        arguments(HMAC_SHA256, SEED_SHA256, 1111111111L, "67062674"),
        arguments(HMAC_SHA256, SEED_SHA256, 1234567890L, "91819424"),
        arguments(HMAC_SHA256, SEED_SHA256, 2000000000L, "90698825"),
        arguments(HMAC_SHA256, SEED_SHA256, 20000000000L, "77737706"),
        arguments(HMAC_SHA512, SEED_SHA512, 59L, "90693936"),
        arguments(HMAC_SHA512, SEED_SHA512, 1111111109L, "25091201"),
        arguments(HMAC_SHA512, SEED_SHA512, 1111111111L, "99943326"),
        arguments(HMAC_SHA512, SEED_SHA512, 1234567890L, "93441116"),
        arguments(HMAC_SHA512, SEED_SHA512, 2000000000L, "38618901"),
        arguments(HMAC_SHA512, SEED_SHA512, 20000000000L, "47863826"));
  }

  static Stream<Arguments> verifiedVectors() {
    return Stream.of(
        arguments(HMAC_SHA1, SEED_SHA1, 1111111109L, "07081804"),
        arguments(HMAC_SHA256, SEED_SHA256, 1234567890L, "91819424"),
        arguments(HMAC_SHA512, SEED_SHA512, 20000000000L, "47863826"));
  }

  @ParameterizedTest(name = "{0} at {2}")
  @MethodSource("vectors")
  void should_match_rfc_6238_vector_when_generating(
      TotpAlgorithm algorithm, String seed, long epochSecond, String expected) {
    var generator = generatorFor(algorithm);

    assertThat(generator.generate(TotpSecret.fromHex(seed), Instant.ofEpochSecond(epochSecond)))
        .isEqualTo(expected);
  }

  @ParameterizedTest(name = "{0} at {2}")
  @MethodSource("verifiedVectors")
  void should_verify_rfc_6238_vector_when_variance_is_zero(
      TotpAlgorithm algorithm, String seed, long epochSecond, String code) {
    var generator = generatorFor(algorithm);
    var at = Instant.ofEpochSecond(epochSecond);

    assertThat(generator.verify(TotpSecret.fromHex(seed), code, 0, at)).isTrue();
  }

  private static TotpGenerator generatorFor(TotpAlgorithm algorithm) {
    return new TotpGenerator(new TotpConfig(Duration.ofSeconds(30), TotpDigits.EIGHT, algorithm));
  }
}
