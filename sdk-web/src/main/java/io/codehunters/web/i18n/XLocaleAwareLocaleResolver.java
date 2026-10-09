package io.codehunters.web.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
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
 * the same language and no country; otherwise the next source is tried. Without supported locales,
 * the highest-weighted {@code Accept-Language} range that names a language is used; wildcards and
 * ranges of weight zero are skipped. Falls through to the configured default rather than the JVM or
 * root locale when no header resolves.
 *
 * <p>Unicode extensions ({@code -u-ca-japanese}) are dropped from the custom header, so a client
 * cannot change calendars or numbering systems. Scripts are kept, so {@code zh-Hant-TW} does not
 * match a supported {@code zh_TW}, as with Spring's own resolver.
 *
 * <p>The locale comes from request headers only: {@link #setLocale} is not supported.
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
    if (getSupportedLocales().isEmpty()) {
      return firstAcceptedLanguage(acceptLanguage).orElseGet(this::getDefaultLocale);
    }
    return super.resolveLocale(request);
  }

  @Override
  public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
    throw new UnsupportedOperationException(
        "The locale is resolved from the "
            + headerName
            + " and Accept-Language request headers and cannot be changed");
  }

  // LanguageRange.parse sorts by weight; ranges of weight zero mean "not acceptable".
  private static Optional<Locale> firstAcceptedLanguage(String acceptLanguage) {
    try {
      return LanguageRange.parse(acceptLanguage).stream()
          .filter(range -> range.getWeight() > 0)
          .map(range -> Locale.forLanguageTag(range.getRange()))
          .filter(locale -> !locale.getLanguage().isEmpty())
          .findFirst();
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
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
    Locale locale = Locale.forLanguageTag(header.strip().replace('_', '-')).stripExtensions();
    return locale.getLanguage().isEmpty() ? Optional.empty() : Optional.of(locale);
  }
}
