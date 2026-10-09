package io.codehunters.web.i18n.config;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the i18n SDK, bound from the {@code codehunters.i18n} prefix. Invalid values
 * fail at startup.
 *
 * @param enabled whether the SDK auto-configuration is active
 * @param basename resource bundle base names, in lookup order
 * @param defaultLocale the locale used when no header resolves, and the bundle fallback
 * @param supportedLocales the locales the application serves; empty accepts any locale. Accepts
 *     {@code en-US} and {@code en_US}. Must include {@code defaultLocale} when not empty.
 * @param headerName the custom header that overrides {@code Accept-Language}
 * @param encoding the bundle encoding
 * @param fallbackToSystemLocale whether a missing bundle falls back to the JVM locale
 */
@ConfigurationProperties(prefix = "codehunters.i18n")
public record I18nProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("classpath:i18n/messages") List<String> basename,
    @DefaultValue("es") Locale defaultLocale,
    @DefaultValue List<Locale> supportedLocales,
    @DefaultValue("X-Locale") String headerName,
    @DefaultValue("UTF-8") Charset encoding,
    @DefaultValue("false") boolean fallbackToSystemLocale) {

  private static final Pattern HTTP_TOKEN = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

  /** Validates the configuration and copies the lists. */
  public I18nProperties {
    basename = requireBasenames(basename);
    requireLanguage(defaultLocale, "codehunters.i18n.default-locale");
    supportedLocales = requireSupported(supportedLocales, defaultLocale);
    requireHeaderName(headerName);
    encoding = encoding != null ? encoding : StandardCharsets.UTF_8;
  }

  private static List<String> requireBasenames(List<String> basename) {
    if (basename == null || basename.isEmpty() || basename.stream().anyMatch(String::isBlank)) {
      throw new IllegalArgumentException(
          "codehunters.i18n.basename must list at least one non-blank base name");
    }
    return List.copyOf(basename);
  }

  private static List<Locale> requireSupported(List<Locale> supported, Locale defaultLocale) {
    List<Locale> copy = supported == null ? List.of() : List.copyOf(supported);
    copy.forEach(locale -> requireLanguage(locale, "codehunters.i18n.supported-locales"));
    if (!copy.isEmpty() && !copy.contains(defaultLocale)) {
      throw new IllegalArgumentException(
          "codehunters.i18n.supported-locales must include codehunters.i18n.default-locale");
    }
    return copy;
  }

  private static void requireLanguage(Locale locale, String property) {
    if (locale == null || locale.getLanguage().isEmpty()) {
      throw new IllegalArgumentException(property + " must name a language");
    }
  }

  private static void requireHeaderName(String headerName) {
    if (headerName == null || !HTTP_TOKEN.matcher(headerName).matches()) {
      throw new IllegalArgumentException(
          "codehunters.i18n.header-name must be an HTTP header name");
    }
  }
}
