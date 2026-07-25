package io.codehunters.i18n.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class I18nPropertiesTest {

  private I18nProperties properties;

  @BeforeEach
  void setUp() {
    properties = new I18nProperties();
  }

  @Test
  @DisplayName("should_use_default_values_when_not_configured")
  void should_use_default_values() {
    assertEquals("classpath:i18n/messages", properties.getBasename());
    assertEquals(Locale.of("es"), properties.getDefaultLocale());
    assertEquals("X-Locale", properties.getHeaderName());
    assertEquals("UTF-8", properties.getEncoding());
    assertFalse(properties.isFallbackToSystemLocale());
  }

  @Test
  @DisplayName("should_update_basename_when_setter_called")
  void should_update_basename() {
    properties.setBasename("classpath:i18n/custom");
    assertEquals("classpath:i18n/custom", properties.getBasename());
  }

  @Test
  @DisplayName("should_update_default_locale_when_setter_called")
  void should_update_default_locale() {
    properties.setDefaultLocale(Locale.of("en"));
    assertEquals(Locale.of("en"), properties.getDefaultLocale());
  }

  @Test
  @DisplayName("should_update_header_name_when_setter_called")
  void should_update_header_name() {
    properties.setHeaderName("X-Lang");
    assertEquals("X-Lang", properties.getHeaderName());
  }

  @Test
  @DisplayName("should_update_encoding_when_setter_called")
  void should_update_encoding() {
    properties.setEncoding("ISO-8859-1");
    assertEquals("ISO-8859-1", properties.getEncoding());
  }

  @Test
  @DisplayName("should_update_fallback_when_setter_called")
  void should_update_fallback() {
    properties.setFallbackToSystemLocale(true);
    assertTrue(properties.isFallbackToSystemLocale());
  }
}
