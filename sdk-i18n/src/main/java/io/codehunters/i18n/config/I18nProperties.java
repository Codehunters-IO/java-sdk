package io.codehunters.i18n.config;

import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the i18n SDK, bound from the {@code vitxo.i18n} prefix. */
@ConfigurationProperties(prefix = "vitxo.i18n")
public class I18nProperties {

  /** Creates the properties with default values. */
  public I18nProperties() {}

  /** Resource bundle base name. Default: {@code classpath:i18n/messages}. */
  private String basename = "classpath:i18n/messages";

  /** Default locale when no header is present. Default: {@code es}. */
  private Locale defaultLocale = Locale.of("es");

  /** Custom header consumers can send to override locale resolution. Default: {@code X-Locale}. */
  private String headerName = "X-Locale";

  /** Bundle encoding. Default: {@code UTF-8}. */
  private String encoding = "UTF-8";

  /** Whether to fall back to the system locale when a key is missing. Default: {@code false}. */
  private boolean fallbackToSystemLocale = false;

  /**
   * Resource bundle base name.
   *
   * @return the resource bundle base name
   */
  public String getBasename() {
    return basename;
  }

  /**
   * Sets the resource bundle base name.
   *
   * @param basename the base name
   */
  public void setBasename(String basename) {
    this.basename = basename;
  }

  /**
   * Default locale used when no header is present.
   *
   * @return the default locale
   */
  public Locale getDefaultLocale() {
    return defaultLocale;
  }

  /**
   * Sets the default locale.
   *
   * @param defaultLocale the default locale
   */
  public void setDefaultLocale(Locale defaultLocale) {
    this.defaultLocale = defaultLocale;
  }

  /**
   * Custom header name used to override locale resolution.
   *
   * @return the header name
   */
  public String getHeaderName() {
    return headerName;
  }

  /**
   * Sets the custom locale header name.
   *
   * @param headerName the header name
   */
  public void setHeaderName(String headerName) {
    this.headerName = headerName;
  }

  /**
   * Resource bundle encoding.
   *
   * @return the resource bundle encoding
   */
  public String getEncoding() {
    return encoding;
  }

  /**
   * Sets the resource bundle encoding.
   *
   * @param encoding the encoding
   */
  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }

  /**
   * Whether to fall back to the system locale when a key is missing.
   *
   * @return {@code true} if the system locale is used as a fallback
   */
  public boolean isFallbackToSystemLocale() {
    return fallbackToSystemLocale;
  }

  /**
   * Sets whether to fall back to the system locale when a key is missing.
   *
   * @param fallbackToSystemLocale {@code true} to fall back to the system locale
   */
  public void setFallbackToSystemLocale(boolean fallbackToSystemLocale) {
    this.fallbackToSystemLocale = fallbackToSystemLocale;
  }
}
