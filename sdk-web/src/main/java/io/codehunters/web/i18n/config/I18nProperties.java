package io.codehunters.web.i18n.config;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the i18n part, bound from the {@code codehunters.web.i18n} prefix. Message
 * bundles are Boot's ({@code spring.messages.*}) and the default locale is Boot's {@code
 * spring.web.locale}. Invalid values fail at startup.
 *
 * @param enabled whether the auto-configuration is active
 * @param supportedLocales the locales the application serves; empty accepts any locale. Accepts
 *     {@code en-US} and {@code en_US}.
 * @param headerName the custom header that overrides {@code Accept-Language}
 * @param mdc the locale-to-MDC filter
 */
@ConfigurationProperties(prefix = "codehunters.web.i18n")
public record I18nProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue List<Locale> supportedLocales,
    @DefaultValue("X-Locale") String headerName,
    @DefaultValue Mdc mdc) {

  private static final Pattern HTTP_TOKEN = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

  /** Validates the configuration and copies the list. */
  public I18nProperties {
    supportedLocales = requireSupported(supportedLocales);
    requireHeaderName(headerName);
    mdc = mdc != null ? mdc : new Mdc(false);
  }

  /**
   * The filter that copies the resolved locale into the SLF4J MDC under {@code locale}.
   *
   * @param enabled whether the filter is registered; off by default
   */
  public record Mdc(@DefaultValue("false") boolean enabled) {}

  private static List<Locale> requireSupported(List<Locale> supported) {
    List<Locale> copy = supported == null ? List.of() : List.copyOf(supported);
    if (copy.stream().anyMatch(locale -> locale.getLanguage().isEmpty())) {
      throw new IllegalArgumentException(
          "codehunters.web.i18n.supported-locales must name a language");
    }
    return copy;
  }

  private static void requireHeaderName(String headerName) {
    if (headerName == null || !HTTP_TOKEN.matcher(headerName).matches()) {
      throw new IllegalArgumentException(
          "codehunters.web.i18n.header-name must be an HTTP header name");
    }
  }
}
