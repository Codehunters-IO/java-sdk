package io.codehunters.web.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

class XLocaleAwareLocaleResolverTest {

  private static final Locale SPANISH = Locale.of("es");
  private static final Locale CONTAINER_LOCALE = Locale.JAPAN;

  private final XLocaleAwareLocaleResolver resolver = new XLocaleAwareLocaleResolver("X-Locale");
  private final MockHttpServletRequest request = new MockHttpServletRequest();
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @Test
  void should_prefer_x_locale_when_accept_language_is_also_sent() {
    request.addHeader("X-Locale", "en");
    request.addHeader("Accept-Language", "es");

    assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.ENGLISH);
  }

  @Test
  void should_use_accept_language_when_x_locale_is_absent() {
    request.addHeader("Accept-Language", "en-US,en;q=0.9");

    assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.US);
  }

  @Test
  void should_use_accept_language_when_x_locale_is_blank() {
    request.addHeader("X-Locale", "  ");
    request.addHeader("Accept-Language", "en");

    assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.ENGLISH);
  }

  @Test
  void should_use_accept_language_when_x_locale_cannot_be_parsed() {
    request.addHeader("X-Locale", "___");
    request.addHeader("Accept-Language", "en");

    assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.ENGLISH);
  }

  @Test
  void should_use_configured_default_locale_when_no_header_is_sent() {
    resolver.setDefaultLocale(SPANISH);

    assertThat(resolver.resolveLocale(request)).isEqualTo(SPANISH);
  }

  @Test
  void should_use_container_locale_when_no_header_is_sent_and_no_default_is_configured() {
    request.setPreferredLocales(List.of(CONTAINER_LOCALE));

    assertThat(resolver.resolveLocale(request)).isEqualTo(CONTAINER_LOCALE);
  }

  @Test
  void should_use_default_locale_when_accept_language_names_no_language() {
    resolver.setDefaultLocale(SPANISH);

    assertThat(resolver.resolveLocale(containerRequest("*", Locale.ROOT))).isEqualTo(SPANISH);
  }

  @Test
  void should_keep_container_locale_when_accept_language_names_no_language_and_no_default() {
    assertThat(resolver.resolveLocale(containerRequest("!!!", Locale.ROOT))).isEqualTo(Locale.ROOT);
  }

  @Test
  void should_normalize_underscore_when_x_locale_uses_java_locale_format() {
    request.addHeader("X-Locale", "en_US");

    assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.US);
  }

  @Test
  void should_drop_unicode_extensions_from_x_locale() {
    request.addHeader("X-Locale", "en-US-u-ca-japanese");

    assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.US);
  }

  @Test
  void should_ignore_x_locale_when_it_is_longer_than_a_language_tag() {
    resolver.setDefaultLocale(SPANISH);
    request.addHeader("X-Locale", "en-US-x-" + "a".repeat(40));

    assertThat(resolver.resolveLocale(request)).isEqualTo(SPANISH);
  }

  @Test
  void should_use_the_locale_set_on_the_request_over_the_headers() {
    request.addHeader("X-Locale", "es");

    resolver.setLocale(request, response, Locale.FRENCH);

    assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.FRENCH);
  }

  @Test
  void should_return_to_the_headers_when_the_set_locale_is_cleared() {
    request.addHeader("X-Locale", "es");
    resolver.setLocale(request, response, Locale.FRENCH);

    resolver.setLocale(request, response, null);

    assertThat(resolver.resolveLocale(request)).isEqualTo(SPANISH);
  }

  @Test
  void should_apply_the_locale_parameter_when_a_locale_change_interceptor_runs() {
    request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, resolver);
    request.addParameter(LocaleChangeInterceptor.DEFAULT_PARAM_NAME, "en");

    assertThatNoException()
        .isThrownBy(() -> new LocaleChangeInterceptor().preHandle(request, response, new Object()));
    assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.ENGLISH);
  }

  // Tomcat answers getLocale() from its own parse of the header; the mock would rewrite the header.
  private static MockHttpServletRequest containerRequest(String acceptLanguage, Locale locale) {
    var containerRequest =
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
    containerRequest.addHeader("Accept-Language", acceptLanguage);
    return containerRequest;
  }
}
