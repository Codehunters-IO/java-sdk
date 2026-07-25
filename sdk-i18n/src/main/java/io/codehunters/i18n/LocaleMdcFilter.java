package io.codehunters.i18n;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Copies the resolved request locale into SLF4J {@link MDC} under key {@code locale} so downstream
 * components (use cases, outbox factories, async tasks) can read it without depending on Spring's
 * {@code LocaleContextHolder}. Pairs with {@code MdcTaskDecorator} for async propagation.
 */
public class LocaleMdcFilter extends OncePerRequestFilter {

  /** MDC key holding the resolved locale language tag. */
  public static final String MDC_LOCALE = "locale";

  private final LocaleResolver localeResolver;

  /**
   * Creates the filter.
   *
   * @param localeResolver the resolver used to determine the request locale
   */
  public LocaleMdcFilter(LocaleResolver localeResolver) {
    this.localeResolver = localeResolver;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String previous = MDC.get(MDC_LOCALE);
    String resolved = resolveLocale(request);
    if (resolved != null) {
      MDC.put(MDC_LOCALE, resolved);
    }
    try {
      filterChain.doFilter(request, response);
    } finally {
      if (previous != null) {
        MDC.put(MDC_LOCALE, previous);
      } else {
        MDC.remove(MDC_LOCALE);
      }
    }
  }

  private String resolveLocale(HttpServletRequest request) {
    try {
      Locale locale = localeResolver.resolveLocale(request);
      return locale.toLanguageTag();
    } catch (RuntimeException e) {
      return null;
    }
  }
}
