package io.codehunters.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Behaviour when no supported locales are configured. The servlet container's own parsing of {@code
 * Accept-Language} is simulated by overriding {@code getLocale()}: Tomcat returns the root locale
 * for {@code *} and garbage, and the JVM locale when every range has weight zero.
 */
class XLocaleAwareLocaleResolverAcceptLanguageTest {

  private static final Locale SPANISH = Locale.of("es");
  private static final Locale JVM_LOCALE = Locale.JAPAN;

  private final XLocaleAwareLocaleResolver resolver =
      new XLocaleAwareLocaleResolver(SPANISH, "X-Locale");
  private final MockHttpServletRequest request = new MockHttpServletRequest();

  @Test
  void should_use_default_locale_when_accept_language_is_a_wildcard() {
    var request = containerRequest("*", Locale.ROOT);

    assertThat(resolver.resolveLocale(request)).isEqualTo(SPANISH);
  }

  @Test
  void should_use_default_locale_when_every_accept_language_range_has_weight_zero() {
    var request = containerRequest("en;q=0", JVM_LOCALE);

    assertThat(resolver.resolveLocale(request)).isEqualTo(SPANISH);
  }

  @Test
  void should_use_default_locale_when_accept_language_cannot_be_parsed() {
    var request = containerRequest("!!!", Locale.ROOT);

    assertThat(resolver.resolveLocale(request)).isEqualTo(SPANISH);
  }

  @Test
  void should_use_highest_weighted_language_when_accept_language_mixes_weights() {
    var request = containerRequest("fr;q=0, de;q=0.5, *;q=0.8", Locale.FRENCH);

    assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.GERMAN);
  }

  @Test
  void should_use_first_accept_language_range() {
    var request = containerRequest("en-US,en;q=0.9", Locale.US);

    assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.US);
  }

  @Test
  void should_drop_unicode_extensions_from_the_override() {
    request.addHeader("X-Locale", "en-US-u-ca-japanese");

    assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.US);
  }

  @Test
  void should_match_supported_locale_when_the_override_carries_extensions() {
    var restricted = new XLocaleAwareLocaleResolver(SPANISH, "X-Locale", List.of(Locale.US));
    request.addHeader("X-Locale", "en-US-u-nu-thai");

    assertThat(restricted.resolveLocale(request)).isEqualTo(Locale.US);
  }

  @Test
  void should_explain_that_the_locale_comes_from_request_headers_when_set_locale_is_called() {
    var response = new MockHttpServletResponse();

    assertThatThrownBy(() -> resolver.setLocale(request, response, Locale.US))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("X-Locale")
        .hasMessageContaining("Accept-Language");
  }

  // Tomcat answers getLocale() from its own parse of the header; the mock would rewrite the header.
  private static MockHttpServletRequest containerRequest(String acceptLanguage, Locale locale) {
    var request =
        new MockHttpServletRequest() {
          @Override
          public Locale getLocale() {
            return locale;
          }

          @Override
          public Enumeration<Locale> getLocales() {
            return Collections.enumeration(List.of(locale));
          }
        };
    request.addHeader("Accept-Language", acceptLanguage);
    return request;
  }
}
