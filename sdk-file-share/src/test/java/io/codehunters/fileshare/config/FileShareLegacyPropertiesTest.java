package io.codehunters.fileshare.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

class FileShareLegacyPropertiesTest {

  private static final String METADATA = "META-INF/spring-configuration-metadata.json";

  private final StandardEnvironment environment = new StandardEnvironment();

  @Test
  void should_bind_legacy_keys_when_set_in_configuration_files() {
    addApplicationProperties(
        Map.of(
            "vitxo.file-share.enabled", "false",
            "vitxo.file-share.base-url", "http://files",
            "vitxo.file-share.connect-timeout", "2s",
            "vitxo.file-share.read-timeout", "9s"));

    FileShareProperties properties = postProcessAndBind();

    assertThat(properties.enabled()).isFalse();
    assertThat(properties.baseUrl()).isEqualTo("http://files");
    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(9));
  }

  @Test
  void should_bind_legacy_keys_when_set_as_environment_variables() {
    environment
        .getPropertySources()
        .replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("VITXO_FILESHARE_BASEURL", "http://env-files")));

    assertThat(postProcessAndBind().baseUrl()).isEqualTo("http://env-files");
  }

  @Test
  void should_keep_new_key_when_both_old_and_new_keys_set() {
    addApplicationProperties(
        Map.of(
            "vitxo.file-share.base-url", "http://old",
            "codehunters.file-share.base-url", "http://new"));

    assertThat(postProcessAndBind().baseUrl()).isEqualTo("http://new");
  }

  @Test
  void should_run_right_after_configuration_files_are_loaded() {
    var postProcessor = new FileShareLegacyProperties(Supplier::get);

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

    assertThat(postProcessors).hasAtLeastOneElementOfType(FileShareLegacyProperties.class);
  }

  @Test
  void should_publish_legacy_keys_as_deprecated_in_configuration_metadata() throws IOException {
    assertThat(readMetadata())
        .contains("\"name\": \"vitxo.file-share.base-url\"")
        .contains("\"replacement\": \"codehunters.file-share.base-url\"")
        .contains("\"replacement\": \"codehunters.file-share.read-timeout\"")
        .contains("\"name\": \"codehunters.file-share.auth.client-registration-id\"");
  }

  private void addApplicationProperties(Map<String, Object> properties) {
    environment.getPropertySources().addFirst(new MapPropertySource("application", properties));
  }

  private FileShareProperties postProcessAndBind() {
    new FileShareLegacyProperties(Supplier::get)
        .postProcessEnvironment(environment, new SpringApplication());
    return Binder.get(environment)
        .bindOrCreate("codehunters.file-share", FileShareProperties.class);
  }

  private static String readMetadata() throws IOException {
    try (InputStream in =
        FileShareLegacyPropertiesTest.class.getClassLoader().getResourceAsStream(METADATA)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
