package io.codehunters.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.servlet.LocaleResolver;

class LocaleMdcFilterTest {

  private LocaleResolver localeResolver;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private FilterChain chain;
  private LocaleMdcFilter filter;

  @BeforeEach
  void setUp() {
    localeResolver = mock(LocaleResolver.class);
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    chain = mock(FilterChain.class);
    filter = new LocaleMdcFilter(localeResolver);
    MDC.clear();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  @DisplayName("should_put_locale_in_mdc_when_resolver_returns_locale")
  void should_put_locale_in_mdc_when_resolver_returns_locale() throws Exception {
    when(localeResolver.resolveLocale(request)).thenReturn(Locale.of("en", "US"));
    doAnswer(
            inv -> {
              assertEquals("en-US", MDC.get(LocaleMdcFilter.MDC_LOCALE));
              return null;
            })
        .when(chain)
        .doFilter(any(), any());

    filter.doFilter(request, response, chain);

    assertNull(MDC.get(LocaleMdcFilter.MDC_LOCALE));
  }

  @Test
  @DisplayName("should_skip_mdc_when_resolver_returns_null")
  void should_skip_mdc_when_resolver_returns_null() throws Exception {
    when(localeResolver.resolveLocale(request)).thenReturn(null);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertNull(MDC.get(LocaleMdcFilter.MDC_LOCALE));
  }

  @Test
  @DisplayName("should_skip_mdc_when_resolver_throws_runtime_exception")
  void should_skip_mdc_when_resolver_throws() throws Exception {
    when(localeResolver.resolveLocale(request)).thenThrow(new RuntimeException("boom"));

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertNull(MDC.get(LocaleMdcFilter.MDC_LOCALE));
  }

  @Test
  @DisplayName("should_restore_previous_mdc_value_when_filter_completes")
  void should_restore_previous_mdc_value() throws Exception {
    MDC.put(LocaleMdcFilter.MDC_LOCALE, "fr-FR");
    when(localeResolver.resolveLocale(request)).thenReturn(Locale.of("en", "US"));

    filter.doFilter(request, response, chain);

    assertEquals("fr-FR", MDC.get(LocaleMdcFilter.MDC_LOCALE));
  }

  @Test
  @DisplayName("should_remove_mdc_when_no_previous_value_existed")
  void should_remove_mdc_when_no_previous_value() throws Exception {
    when(localeResolver.resolveLocale(request)).thenReturn(Locale.of("es"));

    filter.doFilter(request, response, chain);

    assertNull(MDC.get(LocaleMdcFilter.MDC_LOCALE));
  }
}
