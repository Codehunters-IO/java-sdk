package io.codehunters.web.i18n;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Copies the resolved request locale into SLF4J {@link MDC} under key {@code locale} so downstream
 * components (use cases, outbox factories, async tasks) can read it without depending on Spring's
 * {@code LocaleContextHolder}. Runs on async and error dispatches too, so error pages and async
 * completions log with the locale. A resolver failure is logged (exception class only, since the
 * message may echo request headers) and the request continues without the MDC entry.
 */
public class LocaleMdcFilter extends OncePerRequestFilter {

  /** MDC key holding the resolved locale language tag. */
  public static final String MDC_LOCALE = "locale";

  private static final Logger LOG = LoggerFactory.getLogger(LocaleMdcFilter.class);

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
    resolveLocale(request).ifPresent(tag -> MDC.put(MDC_LOCALE, tag));
    try {
      filterChain.doFilter(request, response);
    } finally {
      restore(previous);
    }
  }

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }

  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }

  private Optional<String> resolveLocale(HttpServletRequest request) {
    try {
      return Optional.ofNullable(localeResolver.resolveLocale(request)).map(Locale::toLanguageTag);
    } catch (RuntimeException e) {
      LOG.warn(
          "Could not resolve the request locale ({}); continuing without it",
          e.getClass().getName());
      return Optional.empty();
    }
  }

  private static void restore(String previous) {
    if (previous != null) {
      MDC.put(MDC_LOCALE, previous);
    } else {
      MDC.remove(MDC_LOCALE);
    }
  }
}
