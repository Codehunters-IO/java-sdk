package io.codehunters.i18n.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;

class I18nLegacyPropertiesTest {

  private static final String METADATA = "META-INF/spring-configuration-metadata.json";

  private final StandardEnvironment environment = new StandardEnvironment();

  @Test
  void should_bind_legacy_keys_when_set_in_configuration_files() {
    addApplicationProperties(
        Map.of(
            "vitxo.i18n.basename", "classpath:i18n/custom",
            "vitxo.i18n.default-locale", "en",
            "vitxo.i18n.header-name", "X-Lang",
            "vitxo.i18n.encoding", "ISO-8859-1",
            "vitxo.i18n.fallback-to-system-locale", "true"));

    I18nProperties properties = postProcessAndBind();

    assertThat(properties.basename()).containsExactly("classpath:i18n/custom");
    assertThat(properties.defaultLocale()).isEqualTo(Locale.of("en"));
    assertThat(properties.headerName()).isEqualTo("X-Lang");
    assertThat(properties.encoding()).isEqualTo(StandardCharsets.ISO_8859_1);
    assertThat(properties.fallbackToSystemLocale()).isTrue();
  }

  @Test
  void should_bind_legacy_keys_when_set_as_environment_variables() {
    environment
        .getPropertySources()
        .replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("VITXO_I18N_DEFAULTLOCALE", "en")));

    assertThat(postProcessAndBind().defaultLocale()).isEqualTo(Locale.of("en"));
  }

  @Test
  void should_keep_new_key_when_both_old_and_new_keys_set() {
    addApplicationProperties(
        Map.of("vitxo.i18n.header-name", "X-Old", "codehunters.i18n.header-name", "X-New"));

    assertThat(postProcessAndBind().headerName()).isEqualTo("X-New");
  }

  @Test
  void should_run_right_after_configuration_files_are_loaded() {
    var postProcessor = new I18nLegacyProperties(Supplier::get);

    assertThat(postProcessor.getOrder()).isEqualTo(ConfigDataEnvironmentPostProcessor.ORDER + 1);
  }

  @Test
  void should_be_discovered_by_spring_boot() {
    DeferredLogFactory logFactory = Supplier::get;

    var postProcessors =
        SpringFactoriesLoader.forDefaultResourceLocation()
            .load(
                EnvironmentPostProcessor.class,
                ArgumentResolver.of(DeferredLogFactory.class, logFactory));

    assertThat(postProcessors).hasAtLeastOneElementOfType(I18nLegacyProperties.class);
  }

  @Test
  void should_publish_legacy_keys_as_deprecated_in_configuration_metadata() throws IOException {
    assertThat(readMetadata())
        .contains("\"name\": \"vitxo.i18n.default-locale\"")
        .contains("\"replacement\": \"codehunters.i18n.default-locale\"")
        .contains("\"replacement\": \"codehunters.i18n.fallback-to-system-locale\"")
        .contains("\"name\": \"codehunters.i18n.supported-locales\"");
  }

  private void addApplicationProperties(Map<String, Object> properties) {
    environment.getPropertySources().addFirst(new MapPropertySource("application", properties));
  }

  private I18nProperties postProcessAndBind() {
    new I18nLegacyProperties(Supplier::get)
        .postProcessEnvironment(environment, new SpringApplication());
    return Binder.get(environment).bindOrCreate("codehunters.i18n", I18nProperties.class);
  }

  private static String readMetadata() throws IOException {
    try (InputStream in =
        I18nLegacyPropertiesTest.class.getClassLoader().getResourceAsStream(METADATA)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
