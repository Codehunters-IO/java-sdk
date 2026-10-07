package io.codehunters.i18n;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Resolves the request locale in this priority order:
 *
 * <ol>
 *   <li>Custom header (default {@code X-Locale}) when present, parseable and supported
 *   <li>Standard {@code Accept-Language} header (RFC 9110), matched against the supported locales
 *   <li>Configured default locale
 * </ol>
 *
 * <p>The custom header accepts BCP 47 tags ({@code en-US}) and Java's {@code en_US} form. A value
 * longer than {@value #MAX_OVERRIDE_LENGTH} characters is ignored. When supported locales are
 * configured, a header locale is used if it is supported, or mapped to a supported locale that has
 * the same language and no country; otherwise the next source is tried. Falls through to the
 * configured default rather than the JVM locale when no header resolves.
 */
public class XLocaleAwareLocaleResolver extends AcceptHeaderLocaleResolver {

  /** Longest custom header value considered; longer values are ignored. */
  public static final int MAX_OVERRIDE_LENGTH = 35;

  private static final String ACCEPT_LANGUAGE = "Accept-Language";

  private final String headerName;

  /**
   * Creates a resolver that accepts any locale.
   *
   * @param defaultLocale the locale used when no header resolves
   * @param headerName the custom header inspected before {@code Accept-Language}
   */
  public XLocaleAwareLocaleResolver(Locale defaultLocale, String headerName) {
    this(defaultLocale, headerName, List.of());
  }

  /**
   * Creates a resolver restricted to the given locales.
   *
   * @param defaultLocale the locale used when no header resolves
   * @param headerName the custom header inspected before {@code Accept-Language}
   * @param supportedLocales the locales the application serves; empty accepts any locale
   */
  public XLocaleAwareLocaleResolver(
      Locale defaultLocale, String headerName, List<Locale> supportedLocales) {
    setDefaultLocale(defaultLocale);
    setSupportedLocales(supportedLocales);
    this.headerName = headerName;
  }

  @Override
  public Locale resolveLocale(HttpServletRequest request) {
    return parseOverride(request.getHeader(headerName))
        .flatMap(this::toSupported)
        .orElseGet(() -> resolveFromAcceptLanguage(request));
  }

  private Locale resolveFromAcceptLanguage(HttpServletRequest request) {
    String acceptLanguage = request.getHeader(ACCEPT_LANGUAGE);
    if (acceptLanguage == null || acceptLanguage.isBlank()) {
      return getDefaultLocale();
    }
    return super.resolveLocale(request);
  }

  private Optional<Locale> toSupported(Locale candidate) {
    List<Locale> supported = getSupportedLocales();
    if (supported.isEmpty() || supported.contains(candidate)) {
      return Optional.of(candidate);
    }
    return supported.stream()
        .filter(locale -> locale.getCountry().isEmpty())
        .filter(locale -> locale.getLanguage().equals(candidate.getLanguage()))
        .findFirst();
  }

  private static Optional<Locale> parseOverride(String header) {
    if (header == null || header.isBlank() || header.length() > MAX_OVERRIDE_LENGTH) {
      return Optional.empty();
    }
    Locale locale = Locale.forLanguageTag(header.strip().replace('_', '-'));
    return locale.getLanguage().isEmpty() ? Optional.empty() : Optional.of(locale);
  }
}
