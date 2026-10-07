package io.codehunters.totp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpHardeningTest {

  private static final TotpSecret SECRET =
      TotpSecret.fromHex("3132333435363738393031323334353637383930");
  private static final Instant AT = Instant.ofEpochSecond(59);

  private final TotpGenerator generator = new TotpGenerator();

  @Test
  void should_share_window_with_earlier_instants_when_before_epoch() {
    assertThat(generator.generate(SECRET, Instant.ofEpochSecond(-1)))
        .isEqualTo(generator.generate(SECRET, Instant.ofEpochSecond(-30)));
  }

  @Test
  void should_not_share_window_with_epoch_when_before_epoch() {
    assertThat(generator.generate(SECRET, Instant.ofEpochSecond(-1)))
        .isNotEqualTo(generator.generate(SECRET, Instant.EPOCH));
  }

  @Test
  void should_reject_variance_above_ten() {
    assertThatThrownBy(() -> generator.verify(SECRET, "000000", 11, AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("variance");
  }

  @Test
  void should_reject_negative_variance() {
    assertThatThrownBy(() -> generator.verify(SECRET, "000000", -1, AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("variance");
  }

  @Test
  void should_accept_variance_of_ten() {
    var code = generator.generate(SECRET, AT.plusSeconds(300));

    assertThat(generator.verify(SECRET, code, 10, AT)).isTrue();
  }

  @Test
  void should_reject_null_code() {
    assertThat(generator.verify(SECRET, null, 1, AT)).isFalse();
  }

  @Test
  void should_reject_code_with_extra_characters() {
    var code = generator.generate(SECRET, AT);

    assertThat(generator.verify(SECRET, code + "0", 0, AT)).isFalse();
  }

  @Test
  void should_reject_sub_second_time_step() {
    assertThatThrownBy(
            () -> new TotpConfig(Duration.ofMillis(500), TotpDigits.SIX, TotpAlgorithm.HMAC_SHA1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timeStep");
  }

  @Test
  void should_reject_fractional_time_step() {
    assertThatThrownBy(
            () -> new TotpConfig(Duration.ofMillis(1500), TotpDigits.SIX, TotpAlgorithm.HMAC_SHA1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timeStep");
  }

  @Test
  void should_accept_one_second_time_step() {
    var config = new TotpConfig(Duration.ofSeconds(1), TotpDigits.SIX, TotpAlgorithm.HMAC_SHA1);

    assertThat(config.timeStep()).isEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void should_not_echo_secret_characters_when_base32_is_invalid() {
    assertThatThrownBy(() -> TotpSecret.fromBase32("JBSWY3DP!"))
        .isInstanceOf(TotpException.class)
        .hasMessageNotContaining("!");
  }

  @Test
  void should_create_secret_from_hex() {
    assertThat(TotpSecret.fromHex("48656C6C6F").key()).isEqualTo("Hello".getBytes());
  }

  @Test
  void should_not_echo_input_when_hex_is_invalid() {
    assertThatThrownBy(() -> TotpSecret.fromHex("zz41"))
        .isInstanceOf(TotpException.class)
        .hasMessageNotContaining("zz41")
        .hasNoCause();
  }

  @Test
  void should_reject_odd_length_hex() {
    assertThatThrownBy(() -> TotpSecret.fromHex("abc")).isInstanceOf(TotpException.class);
  }

  @Test
  void should_reject_null_hex() {
    assertThatThrownBy(() -> TotpSecret.fromHex(null)).isInstanceOf(NullPointerException.class);
  }
}
