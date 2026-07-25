package io.codehunters.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class XLocaleAwareLocaleResolverTest {

  private static final Locale DEFAULT = Locale.of("es");

  private XLocaleAwareLocaleResolver resolver;
  private HttpServletRequest request;

  @BeforeEach
  void setUp() {
    resolver = new XLocaleAwareLocaleResolver(DEFAULT, "X-Locale");
    request = mock(HttpServletRequest.class);
  }

  @Test
  @DisplayName("returns default locale when no headers present")
  void returns_default_when_no_headers() {
    when(request.getHeader("X-Locale")).thenReturn(null);
    when(request.getHeader("Accept-Language")).thenReturn(null);

    assertEquals(DEFAULT, resolver.resolveLocale(request));
  }

  @Test
  @DisplayName("X-Locale overrides Accept-Language")
  void x_locale_overrides_accept_language() {
    when(request.getHeader("X-Locale")).thenReturn("en");
    when(request.getHeader("Accept-Language")).thenReturn("es");

    assertEquals("en", resolver.resolveLocale(request).getLanguage());
  }

  @Test
  @DisplayName("Accept-Language wins when X-Locale missing")
  void accept_language_used() {
    when(request.getHeader("X-Locale")).thenReturn(null);
    when(request.getHeader("Accept-Language")).thenReturn("en-US,en;q=0.9");
    when(request.getLocales())
        .thenReturn(java.util.Collections.enumeration(java.util.List.of(Locale.of("en", "US"))));
    when(request.getLocale()).thenReturn(Locale.of("en", "US"));

    assertEquals("en", resolver.resolveLocale(request).getLanguage());
  }

  @Test
  @DisplayName("falls back to default when X-Locale blank and no Accept-Language")
  void blank_x_locale_falls_back() {
    when(request.getHeader("X-Locale")).thenReturn("  ");
    when(request.getHeader("Accept-Language")).thenReturn(null);

    assertEquals(DEFAULT, resolver.resolveLocale(request));
  }

  @Test
  @DisplayName("falls back to default when X-Locale unparseable")
  void invalid_x_locale_falls_back() {
    when(request.getHeader("X-Locale")).thenReturn("___");
    when(request.getHeader("Accept-Language")).thenReturn(null);

    assertEquals(DEFAULT, resolver.resolveLocale(request));
  }

  @Test
  @DisplayName("falls back to default when Accept-Language is blank")
  void blank_accept_language_falls_back() {
    when(request.getHeader("X-Locale")).thenReturn(null);
    when(request.getHeader("Accept-Language")).thenReturn("  ");

    assertEquals(DEFAULT, resolver.resolveLocale(request));
  }
}
