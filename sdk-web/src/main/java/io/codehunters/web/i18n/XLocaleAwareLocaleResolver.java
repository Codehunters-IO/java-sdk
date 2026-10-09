package io.codehunters.web.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Spring's {@link AcceptHeaderLocaleResolver} with a custom header (default {@code X-Locale}) that
 * takes precedence over {@code Accept-Language}. The request locale is, in order:
 *
 * <ol>
 *   <li>The locale set on this request through {@link #setLocale}, e.g. by a {@code
 *       LocaleChangeInterceptor}, when supported
 *   <li>The custom header, when present, parseable and supported
 *   <li>What {@link AcceptHeaderLocaleResolver} answers: {@code Accept-Language} matched against
 *       the supported locales, else the default locale, else the container's locale (the JVM's when
 *       the request has no {@code Accept-Language})
 * </ol>
 *
 * <p>With supported locales, the answer is always one of them: when neither header nor the
 * container's locale match, the default locale applies if it is supported, else the first supported
 * locale.
 *
 * <p>The custom header accepts BCP 47 tags ({@code en-US}) and Java's {@code en_US} form; values
 * longer than {@value #MAX_OVERRIDE_LENGTH} characters are ignored. Unicode extensions ({@code
 * -u-ca-japanese}) are dropped, so a client cannot change calendars or numbering systems. With
 * supported locales, a locale is used if it is supported, or mapped to a supported locale with the
 * same language and no country, as Spring does for {@code Accept-Language}. An {@code
 * Accept-Language} that names no language ({@code *}, garbage) gives the default locale when one is
 * set, never the root locale.
 */
public class XLocaleAwareLocaleResolver extends AcceptHeaderLocaleResolver {

  /** Longest custom header value considered; longer values are ignored. */
  public static final int MAX_OVERRIDE_LENGTH = 35;

  private static final String LOCALE_ATTRIBUTE =
      XLocaleAwareLocaleResolver.class.getName() + ".LOCALE";

  private final String headerName;

  /**
   * Creates a resolver that accepts any locale.
   *
   * @param headerName the custom header inspected before {@code Accept-Language}
   */
  public XLocaleAwareLocaleResolver(String headerName) {
    this(headerName, List.of());
  }

  /**
   * Creates a resolver restricted to the given locales.
   *
   * @param headerName the custom header inspected before {@code Accept-Language}
   * @param supportedLocales the locales the application serves; empty accepts any locale
   */
  public XLocaleAwareLocaleResolver(String headerName, List<Locale> supportedLocales) {
    setSupportedLocales(supportedLocales);
    this.headerName = headerName;
  }

  @Override
  public Locale resolveLocale(HttpServletRequest request) {
    return chosenLocale(request)
        .or(() -> parseOverride(request.getHeader(headerName)).flatMap(this::toSupported))
        .orElseGet(() -> resolveFromAcceptLanguage(request));
  }

  /**
   * Uses the locale for the rest of this request, ahead of the headers. An unsupported locale, or
   * {@code null}, returns the request to header resolution. Nothing is stored beyond the request.
   *
   * @param request the current request
   * @param response the current response, unused
   * @param locale the locale to use, or {@code null} to clear it
   */
  @Override
  public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
    Optional.ofNullable(locale)
        .flatMap(this::toSupported)
        .ifPresentOrElse(
            chosen -> request.setAttribute(LOCALE_ATTRIBUTE, chosen),
            () -> request.removeAttribute(LOCALE_ATTRIBUTE));
  }

  private Locale resolveFromAcceptLanguage(HttpServletRequest request) {
    Locale resolved = super.resolveLocale(request);
    List<Locale> supported = getSupportedLocales();
    if (!supported.isEmpty()) {
      return supported.contains(resolved) ? resolved : supportedFallback(supported);
    }
    Locale defaultLocale = getDefaultLocale();
    return resolved.getLanguage().isEmpty() && defaultLocale != null ? defaultLocale : resolved;
  }

  private Locale supportedFallback(List<Locale> supported) {
    Locale defaultLocale = getDefaultLocale();
    return defaultLocale != null && supported.contains(defaultLocale)
        ? defaultLocale
        : supported.getFirst();
  }

  private static Optional<Locale> chosenLocale(HttpServletRequest request) {
    return request.getAttribute(LOCALE_ATTRIBUTE) instanceof Locale chosen
        ? Optional.of(chosen)
        : Optional.empty();
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
