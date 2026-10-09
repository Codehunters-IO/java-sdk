package io.codehunters.commons.totp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TotpProvisioningUriTest {

  private static final TotpSecret SECRET = TotpSecret.fromBase32("JBSWY3DPEHPK3PXP");

  private final TotpGenerator generator = new TotpGenerator();

  @Test
  void should_build_key_uri_when_default_config_is_used() {
    assertThat(generator.provisioningUri(SECRET, "Example", "alice@google.com"))
        .isEqualTo(
            "otpauth://totp/Example:alice%40google.com?secret=JBSWY3DPEHPK3PXP"
                + "&issuer=Example&algorithm=SHA1&digits=6&period=30");
  }

  @Test
  void should_percent_encode_spaces_when_issuer_has_them() {
    assertThat(generator.provisioningUri(SECRET, "ACME Co", "john.doe@email.com"))
        .isEqualTo(
            "otpauth://totp/ACME%20Co:john.doe%40email.com?secret=JBSWY3DPEHPK3PXP"
                + "&issuer=ACME%20Co&algorithm=SHA1&digits=6&period=30");
  }

  @Test
  void should_percent_encode_reserved_characters_when_account_has_them() {
    assertThat(generator.provisioningUri(SECRET, "Example", "a+b&c=d/e?f#g%h"))
        .startsWith("otpauth://totp/Example:a%2Bb%26c%3Dd%2Fe%3Ff%23g%25h?");
  }

  @Test
  void should_keep_unreserved_characters_when_account_has_them() {
    assertThat(generator.provisioningUri(SECRET, "Example", "Az09-._~"))
        .startsWith("otpauth://totp/Example:Az09-._~?");
  }

  @Test
  void should_encode_utf8_bytes_when_issuer_is_not_ascii() {
    assertThat(generator.provisioningUri(SECRET, "Café", "alice"))
        .startsWith("otpauth://totp/Caf%C3%A9:alice?")
        .contains("&issuer=Caf%C3%A9&");
  }

  @Test
  void should_omit_base32_padding_when_secret_is_padded() {
    var padded = TotpSecret.fromBytes("Hello!".getBytes());

    assertThat(generator.provisioningUri(padded, "Example", "alice"))
        .contains("?secret=JBSWY3DPEE&");
  }

  @Test
  void should_describe_config_when_it_is_not_the_default() {
    var config =
        new TotpConfig(Duration.ofSeconds(60), TotpDigits.EIGHT, TotpAlgorithm.HMAC_SHA512);

    assertThat(new TotpGenerator(config).provisioningUri(SECRET, "Example", "alice"))
        .endsWith("&algorithm=SHA512&digits=8&period=60");
  }

  @Test
  void should_name_sha256_when_config_uses_it() {
    var config = new TotpConfig(Duration.ofSeconds(30), TotpDigits.SIX, TotpAlgorithm.HMAC_SHA256);

    assertThat(new TotpGenerator(config).provisioningUri(SECRET, "Example", "alice"))
        .contains("&algorithm=SHA256&");
  }

  @Test
  void should_reject_issuer_when_it_contains_a_colon() {
    assertThatThrownBy(() -> generator.provisioningUri(SECRET, "Evil:Corp", "alice"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("issuer")
        .hasMessageNotContaining("Evil");
  }

  @Test
  void should_reject_account_when_it_contains_a_colon() {
    assertThatThrownBy(() -> generator.provisioningUri(SECRET, "Example", "a:lice"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("account");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Exa\nmple", "Exa\u0000mple", "Exa\u007Fmple", "Exa\u0085mple"})
  void should_reject_issuer_when_it_contains_a_control_character(String issuer) {
    assertThatThrownBy(() -> generator.provisioningUri(SECRET, issuer, "alice"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("issuer");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ali\u202Ece", "ali\u200Bce", "ali\u2066ce"})
  void should_reject_account_when_it_contains_an_invisible_format_character(String account) {
    assertThatThrownBy(() -> generator.provisioningUri(SECRET, "Example", account))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("account must not contain ':', control or format characters");
  }

  @Test
  void should_reject_account_when_it_contains_a_control_character() {
    assertThatThrownBy(() -> generator.provisioningUri(SECRET, "Example", "ali\tce"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("account");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void should_reject_issuer_when_it_is_blank(String issuer) {
    assertThatThrownBy(() -> generator.provisioningUri(SECRET, issuer, "alice"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("issuer");
  }

  @Test
  void should_reject_account_when_it_is_blank() {
    assertThatThrownBy(() -> generator.provisioningUri(SECRET, "Example", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("account");
  }

  @Test
  void should_reject_null_issuer() {
    assertThatThrownBy(() -> generator.provisioningUri(SECRET, null, "alice"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("issuer");
  }

  @Test
  void should_reject_null_account() {
    assertThatThrownBy(() -> generator.provisioningUri(SECRET, "Example", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("account");
  }

  @Test
  void should_reject_null_secret() {
    assertThatThrownBy(() -> generator.provisioningUri(null, "Example", "alice"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("secret");
  }
}
