package io.codehunters.totp.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.totp.TotpAlgorithm;
import io.codehunters.totp.TotpDigits;
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

class TotpLegacyPropertiesTest {

  private static final String METADATA = "META-INF/spring-configuration-metadata.json";

  private final StandardEnvironment environment = new StandardEnvironment();

  @Test
  void should_bind_legacy_keys_when_set_in_configuration_files() {
    addApplicationProperties(
        Map.of(
            "vitxo.totp.time-step", "60s",
            "vitxo.totp.digits", "eight",
            "vitxo.totp.algorithm", "hmac-sha256"));

    TotpProperties properties = postProcessAndBind();

    assertThat(properties.timeStep()).isEqualTo(Duration.ofSeconds(60));
    assertThat(properties.digits()).isEqualTo(TotpDigits.EIGHT);
    assertThat(properties.algorithm()).isEqualTo(TotpAlgorithm.HMAC_SHA256);
  }

  @Test
  void should_bind_legacy_keys_when_set_as_environment_variables() {
    environment
        .getPropertySources()
        .replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("VITXO_TOTP_TIMESTEP", "45s")));

    assertThat(postProcessAndBind().timeStep()).isEqualTo(Duration.ofSeconds(45));
  }

  @Test
  void should_keep_new_key_when_both_old_and_new_keys_set() {
    addApplicationProperties(
        Map.of("vitxo.totp.time-step", "60s", "codehunters.totp.time-step", "90s"));

    assertThat(postProcessAndBind().timeStep()).isEqualTo(Duration.ofSeconds(90));
  }

  @Test
  void should_run_right_after_configuration_files_are_loaded() {
    var postProcessor = new TotpLegacyProperties(Supplier::get);

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

    assertThat(postProcessors).hasAtLeastOneElementOfType(TotpLegacyProperties.class);
  }

  @Test
  void should_publish_legacy_keys_as_deprecated_in_configuration_metadata() throws IOException {
    assertThat(readMetadata())
        .contains("\"name\": \"vitxo.totp.time-step\"")
        .contains("\"replacement\": \"codehunters.totp.time-step\"")
        .contains("\"replacement\": \"codehunters.totp.digits\"")
        .contains("\"replacement\": \"codehunters.totp.algorithm\"");
  }

  private void addApplicationProperties(Map<String, Object> properties) {
    environment.getPropertySources().addFirst(new MapPropertySource("application", properties));
  }

  private TotpProperties postProcessAndBind() {
    new TotpLegacyProperties(Supplier::get)
        .postProcessEnvironment(environment, new SpringApplication());
    return Binder.get(environment).bindOrCreate("codehunters.totp", TotpProperties.class);
  }

  private static String readMetadata() throws IOException {
    try (InputStream in =
        TotpLegacyPropertiesTest.class.getClassLoader().getResourceAsStream(METADATA)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
