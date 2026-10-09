package io.codehunters.web.i18n.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class I18nPropertiesTest {

  private static final Locale SPANISH = Locale.of("es");
  private static final I18nProperties.Mdc NO_MDC = new I18nProperties.Mdc(false);

  @Test
  void should_use_default_values_when_nothing_is_configured() {
    I18nProperties properties = bind(Map.of());

    assertThat(properties.enabled()).isTrue();
    assertThat(properties.supportedLocales()).isEmpty();
    assertThat(properties.headerName()).isEqualTo("X-Locale");
    assertThat(properties.mdc().enabled()).isFalse();
  }

  @Test
  void should_enable_the_mdc_filter_when_configured() {
    I18nProperties properties = bind(Map.of("codehunters.web.i18n.mdc.enabled", "true"));

    assertThat(properties.mdc().enabled()).isTrue();
  }

  @Test
  void should_bind_supported_locales_when_written_as_language_tags_or_java_locales() {
    I18nProperties properties =
        bind(Map.of("codehunters.web.i18n.supported-locales", "es,en-US,pt_BR"));

    assertThat(properties.supportedLocales())
        .containsExactly(SPANISH, Locale.of("en", "US"), Locale.of("pt", "BR"));
  }

  @Test
  void should_reject_header_name_when_it_is_not_an_http_token() {
    assertThatThrownBy(() -> bind(Map.of("codehunters.web.i18n.header-name", "X Locale")))
        .isInstanceOf(BindException.class)
        .rootCause()
        .hasMessageContaining("codehunters.web.i18n.header-name");
  }

  @Test
  void should_reject_supported_locale_when_it_has_no_language() {
    List<Locale> supported = List.of(SPANISH, Locale.ROOT);

    assertThatThrownBy(() -> new I18nProperties(true, supported, "X-Locale", NO_MDC))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("codehunters.web.i18n.supported-locales");
  }

  @Test
  void should_disable_the_mdc_filter_when_constructed_without_mdc_settings() {
    var properties = new I18nProperties(true, List.of(), "X-Locale", null);

    assertThat(properties.mdc().enabled()).isFalse();
  }

  @Test
  void should_copy_supported_locales_when_constructed_from_a_mutable_list() {
    List<Locale> supported = new ArrayList<>(List.of(SPANISH));
    var properties = new I18nProperties(true, supported, "X-Locale", NO_MDC);

    supported.add(Locale.ENGLISH);

    assertThat(properties.supportedLocales()).containsExactly(SPANISH);
  }

  private static I18nProperties bind(Map<String, String> values) {
    return new Binder(new MapConfigurationPropertySource(values))
        .bindOrCreate("codehunters.web.i18n", I18nProperties.class);
  }
}
