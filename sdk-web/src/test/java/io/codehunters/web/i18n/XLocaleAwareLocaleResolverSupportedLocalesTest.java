package io.codehunters.web.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class XLocaleAwareLocaleResolverSupportedLocalesTest {

  private static final Locale SPANISH = Locale.of("es");
  private static final Locale ENGLISH = Locale.of("en");
  private static final Locale US_ENGLISH = Locale.of("en", "US");

  private final MockHttpServletRequest request = new MockHttpServletRequest();

  @Test
  void should_normalize_underscore_when_override_uses_java_locale_format() {
    request.addHeader("X-Locale", "en_US");

    assertThat(resolverSupporting().resolveLocale(request)).isEqualTo(US_ENGLISH);
  }

  @Test
  void should_ignore_override_when_header_is_longer_than_a_language_tag() {
    request.addHeader("X-Locale", "en-US-x-" + "a".repeat(40));

    assertThat(resolverSupporting().resolveLocale(request)).isEqualTo(SPANISH);
  }

  @Test
  void should_use_override_when_it_is_a_supported_locale() {
    request.addHeader("X-Locale", "en-US");

    assertThat(resolverSupporting(SPANISH, US_ENGLISH).resolveLocale(request))
        .isEqualTo(US_ENGLISH);
  }

  @Test
  void should_use_supported_language_when_override_country_is_not_supported() {
    request.addHeader("X-Locale", "en-GB");

    assertThat(resolverSupporting(SPANISH, ENGLISH).resolveLocale(request)).isEqualTo(ENGLISH);
  }

  @Test
  void should_ignore_override_when_its_language_is_not_supported() {
    request.addHeader("X-Locale", "fr");

    assertThat(resolverSupporting(SPANISH, ENGLISH).resolveLocale(request)).isEqualTo(SPANISH);
  }

  @Test
  void should_fall_back_to_accept_language_when_override_is_not_supported() {
    request.addHeader("X-Locale", "fr");
    request.addHeader("Accept-Language", "en");

    assertThat(resolverSupporting(SPANISH, ENGLISH).resolveLocale(request)).isEqualTo(ENGLISH);
  }

  @Test
  void should_use_default_locale_when_accept_language_is_not_supported() {
    request.addHeader("Accept-Language", "fr-FR,fr;q=0.9");

    assertThat(resolverSupporting(SPANISH, ENGLISH).resolveLocale(request)).isEqualTo(SPANISH);
  }

  @Test
  void should_use_supported_language_when_accept_language_country_is_not_supported() {
    request.addHeader("Accept-Language", "en-GB");

    assertThat(resolverSupporting(SPANISH, ENGLISH).resolveLocale(request)).isEqualTo(ENGLISH);
  }

  @Test
  void should_expose_supported_locales_when_configured() {
    assertThat(resolverSupporting(SPANISH, ENGLISH).getSupportedLocales())
        .containsExactly(SPANISH, ENGLISH);
  }

  private static XLocaleAwareLocaleResolver resolverSupporting(Locale... supported) {
    return new XLocaleAwareLocaleResolver(SPANISH, "X-Locale", List.of(supported));
  }
}
