package io.codehunters.commons.totp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.LOCALE)
class TotpLocaleTest {

  private static final Locale ARABIC_DIGITS = Locale.forLanguageTag("ar-SA-u-nu-arab");
  private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

  private Locale original;

  @BeforeEach
  void rememberLocale() {
    original = Locale.getDefault();
  }

  @AfterEach
  void restoreLocale() {
    Locale.setDefault(original);
  }

  @Test
  void should_pad_with_ascii_digits_when_default_locale_uses_other_digits() {
    Locale.setDefault(ARABIC_DIGITS);
    var generator =
        new TotpGenerator(
            new TotpConfig(Duration.ofSeconds(30), TotpDigits.EIGHT, TotpAlgorithm.HMAC_SHA1));
    var secret = TotpSecret.fromHex("3132333435363738393031323334353637383930");

    assertThat(generator.generate(secret, Instant.ofEpochSecond(1111111109))).isEqualTo("07081804");
  }

  @Test
  void should_decode_lowercase_base32_when_default_locale_is_turkish() {
    Locale.setDefault(TURKISH);

    assertThat(Base32.decode("mzxw6ytboi")).isEqualTo("foobar".getBytes());
  }
}
