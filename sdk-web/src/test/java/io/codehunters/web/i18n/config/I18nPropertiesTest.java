package io.codehunters.web.i18n.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class I18nPropertiesTest {

  private static final Locale SPANISH = Locale.of("es");

  @Test
  void should_use_default_values_when_nothing_is_configured() {
    I18nProperties properties = bind(Map.of());

    assertThat(properties.enabled()).isTrue();
    assertThat(properties.basename()).containsExactly("classpath:i18n/messages");
    assertThat(properties.defaultLocale()).isEqualTo(SPANISH);
    assertThat(properties.supportedLocales()).isEmpty();
    assertThat(properties.headerName()).isEqualTo("X-Locale");
    assertThat(properties.encoding()).isEqualTo(StandardCharsets.UTF_8);
    assertThat(properties.fallbackToSystemLocale()).isFalse();
  }

  @Test
  void should_bind_several_basenames_when_given_as_a_comma_separated_list() {
    I18nProperties properties =
        bind(
            Map.of(
                "codehunters.web.i18n.basename", "classpath:i18n/messages,classpath:i18n/errors"));

    assertThat(properties.basename())
        .containsExactly("classpath:i18n/messages", "classpath:i18n/errors");
  }

  @Test
  void should_bind_supported_locales_when_written_as_language_tags_or_java_locales() {
    I18nProperties properties =
        bind(Map.of("codehunters.web.i18n.supported-locales", "es,en-US,pt_BR"));

    assertThat(properties.supportedLocales())
        .containsExactly(SPANISH, Locale.of("en", "US"), Locale.of("pt", "BR"));
  }

  @Test
  void should_reject_supported_locales_when_they_do_not_include_the_default_locale() {
    assertThatThrownBy(() -> bind(Map.of("codehunters.web.i18n.supported-locales", "en,pt")))
        .isInstanceOf(BindException.class)
        .rootCause()
        .hasMessageContaining("codehunters.web.i18n.supported-locales")
        .hasMessageContaining("codehunters.web.i18n.default-locale");
  }

  @Test
  void should_reject_header_name_when_it_is_not_an_http_token() {
    assertThatThrownBy(() -> bind(Map.of("codehunters.web.i18n.header-name", "X Locale")))
        .isInstanceOf(BindException.class)
        .rootCause()
        .hasMessageContaining("codehunters.web.i18n.header-name");
  }

  @Test
  void should_reject_encoding_when_charset_is_unknown() {
    assertThatThrownBy(() -> bind(Map.of("codehunters.web.i18n.encoding", "no-such-charset")))
        .isInstanceOf(BindException.class);
  }

  @Test
  void should_reject_basename_when_list_is_empty() {
    assertThatThrownBy(
            () -> new I18nProperties(true, List.of(), SPANISH, List.of(), "X-Locale", null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("codehunters.web.i18n.basename");
  }

  @Test
  void should_reject_basename_when_an_entry_is_blank() {
    assertThatThrownBy(
            () ->
                new I18nProperties(
                    true, List.of("classpath:a", " "), SPANISH, List.of(), "X-Locale", null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("codehunters.web.i18n.basename");
  }

  @Test
  void should_reject_default_locale_when_it_has_no_language() {
    assertThatThrownBy(
            () ->
                new I18nProperties(
                    true, List.of("classpath:a"), Locale.ROOT, List.of(), "X-Locale", null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("codehunters.web.i18n.default-locale");
  }

  @Test
  void should_reject_supported_locale_when_it_has_no_language() {
    assertThatThrownBy(
            () ->
                new I18nProperties(
                    true,
                    List.of("classpath:a"),
                    SPANISH,
                    List.of(SPANISH, Locale.ROOT),
                    "X-Locale",
                    null,
                    false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("codehunters.web.i18n.supported-locales");
  }

  @Test
  void should_default_encoding_to_utf8_when_constructed_without_one() {
    I18nProperties properties =
        new I18nProperties(
            true, List.of("classpath:a"), SPANISH, List.of(), "X-Locale", null, false);

    assertThat(properties.encoding()).isEqualTo(StandardCharsets.UTF_8);
  }

  @Test
  void should_copy_lists_when_constructed_from_mutable_lists() {
    List<String> basenames = new java.util.ArrayList<>(List.of("classpath:a"));
    I18nProperties properties =
        new I18nProperties(true, basenames, SPANISH, List.of(), "X-Locale", null, false);

    basenames.add("classpath:b");

    assertThat(properties.basename()).containsExactly("classpath:a");
  }

  private static I18nProperties bind(Map<String, String> values) {
    return new Binder(new MapConfigurationPropertySource(values))
        .bindOrCreate("codehunters.web.i18n", I18nProperties.class);
  }
}
