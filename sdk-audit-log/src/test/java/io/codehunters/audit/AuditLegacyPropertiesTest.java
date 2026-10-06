package io.codehunters.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

class AuditLegacyPropertiesTest {

  private static final String METADATA = "META-INF/spring-configuration-metadata.json";

  private final StandardEnvironment environment = new StandardEnvironment();

  @Test
  void should_bind_legacy_key_when_set_in_configuration_files() {
    addApplicationProperties(Map.of("vitxo.audit.fail-on-error", "true"));

    assertThat(postProcessAndBind().failOnError()).isTrue();
  }

  @Test
  void should_bind_legacy_key_when_set_as_environment_variable() {
    environment
        .getPropertySources()
        .replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("VITXO_AUDIT_FAILONERROR", "true")));

    assertThat(postProcessAndBind().failOnError()).isTrue();
  }

  @Test
  void should_keep_new_key_when_both_old_and_new_keys_set() {
    addApplicationProperties(
        Map.of("vitxo.audit.fail-on-error", "true", "codehunters.audit.fail-on-error", "false"));

    assertThat(postProcessAndBind().failOnError()).isFalse();
  }

  @Test
  void should_run_right_after_configuration_files_are_loaded() {
    var postProcessor = new AuditLegacyProperties(Supplier::get);

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

    assertThat(postProcessors).hasAtLeastOneElementOfType(AuditLegacyProperties.class);
  }

  @Test
  void should_publish_legacy_key_as_deprecated_in_configuration_metadata() throws IOException {
    assertThat(readMetadata())
        .contains("\"name\": \"vitxo.audit.fail-on-error\"")
        .contains("\"replacement\": \"codehunters.audit.fail-on-error\"")
        .contains("\"name\": \"codehunters.audit.async.queue-capacity\"");
  }

  private void addApplicationProperties(Map<String, Object> properties) {
    environment.getPropertySources().addFirst(new MapPropertySource("application", properties));
  }

  private AuditProperties postProcessAndBind() {
    new AuditLegacyProperties(Supplier::get)
        .postProcessEnvironment(environment, new SpringApplication());
    return Binder.get(environment).bindOrCreate("codehunters.audit", AuditProperties.class);
  }

  private static String readMetadata() throws IOException {
    try (InputStream in =
        AuditLegacyPropertiesTest.class.getClassLoader().getResourceAsStream(METADATA)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
