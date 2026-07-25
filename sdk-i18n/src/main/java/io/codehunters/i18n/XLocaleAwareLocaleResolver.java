package io.codehunters.i18n;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Resolves the request locale in this priority order:
 *
 * <ol>
 *   <li>Custom header (default {@code X-Locale}) when present and parseable
 *   <li>Standard {@code Accept-Language} header (RFC 7231)
 *   <li>Configured default locale
 * </ol>
 *
 * Falls through to the configured default rather than the JVM locale when no header is present.
 */
public class XLocaleAwareLocaleResolver extends AcceptHeaderLocaleResolver {

  private final String headerName;

  /**
   * Creates the resolver.
   *
   * @param defaultLocale the locale used when no header resolves
   * @param headerName the custom header inspected before {@code Accept-Language}
   */
  public XLocaleAwareLocaleResolver(Locale defaultLocale, String headerName) {
    setDefaultLocale(defaultLocale);
    this.headerName = headerName;
  }

  @Override
  public Locale resolveLocale(HttpServletRequest request) {
    Locale override = parseOverride(request.getHeader(headerName));
    if (override != null) {
      return override;
    }
    String acceptLanguage = request.getHeader("Accept-Language");
    if (acceptLanguage != null && !acceptLanguage.isBlank()) {
      return super.resolveLocale(request);
    }
    return getDefaultLocale();
  }

  private static Locale parseOverride(String header) {
    if (header == null || header.isBlank()) {
      return null;
    }
    try {
      Locale locale = Locale.forLanguageTag(header.trim());
      return locale.getLanguage().isEmpty() ? null : locale;
    } catch (RuntimeException ex) {
      return null;
    }
  }
}
