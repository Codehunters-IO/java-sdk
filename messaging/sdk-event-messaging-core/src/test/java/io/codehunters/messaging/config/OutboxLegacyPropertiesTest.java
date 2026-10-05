package io.codehunters.messaging.config;

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

class OutboxLegacyPropertiesTest {

  private static final String METADATA = "META-INF/spring-configuration-metadata.json";

  private final StandardEnvironment environment = new StandardEnvironment();

  @Test
  void should_bind_legacy_keys_when_set_in_configuration_files() {
    addApplicationProperties(
        Map.of(
            "outbox.batch-size", "10",
            "outbox.publisher.type", "kafka",
            "outbox.claim.ttl-ms", "900000",
            "outbox.task.fixed-delay", "1000",
            "outbox.purge.retention", "3d"));

    EventMessagingProperties properties = postProcessAndBind();

    assertThat(properties.batchSize()).isEqualTo(10);
    assertThat(properties.publisher().type()).isEqualTo("kafka");
    assertThat(properties.claim().ttlMs()).isEqualTo(900_000L);
    assertThat(properties.task().fixedDelay()).isEqualTo("1000");
    assertThat(properties.purge().retention()).isEqualTo(Duration.ofDays(3));
  }

  @Test
  void should_bind_legacy_keys_when_set_as_environment_variables() {
    environment
        .getPropertySources()
        .replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of(
                    "OUTBOX_BATCHSIZE", "7",
                    "OUTBOX_MAXATTEMPTS", "9",
                    "OUTBOX_PUBLISHER_TYPE", "kafka")));

    EventMessagingProperties properties = postProcessAndBind();

    assertThat(properties.batchSize()).isEqualTo(7);
    assertThat(properties.maxAttempts()).isEqualTo(9);
    assertThat(properties.publisher().type()).isEqualTo("kafka");
  }

  @Test
  void should_keep_new_key_when_both_old_and_new_keys_set() {
    addApplicationProperties(
        Map.of("outbox.batch-size", "10", "codehunters.outbox.batch-size", "20"));

    EventMessagingProperties properties = postProcessAndBind();

    assertThat(properties.batchSize()).isEqualTo(20);
  }

  @Test
  void should_run_right_after_configuration_files_are_loaded() {
    var postProcessor = new OutboxLegacyProperties(Supplier::get);

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

    assertThat(postProcessors).hasAtLeastOneElementOfType(OutboxLegacyProperties.class);
  }

  @Test
  void should_publish_legacy_keys_as_deprecated_in_configuration_metadata() throws IOException {
    String metadata = readMetadata();

    assertThat(metadata)
        .contains("\"name\": \"outbox.batch-size\"")
        .contains("\"replacement\": \"codehunters.outbox.batch-size\"")
        .contains("\"replacement\": \"codehunters.outbox.purge.lock-at-least\"")
        .contains("\"name\": \"codehunters.outbox.purge.fixed-delay\"")
        .contains("\"name\": \"codehunters.outbox.publisher.rabbit.confirm-timeout\"");
  }

  private void addApplicationProperties(Map<String, Object> properties) {
    environment.getPropertySources().addFirst(new MapPropertySource("application", properties));
  }

  private EventMessagingProperties postProcessAndBind() {
    new OutboxLegacyProperties(Supplier::get)
        .postProcessEnvironment(environment, new SpringApplication());
    return Binder.get(environment)
        .bindOrCreate("codehunters.outbox", EventMessagingProperties.class);
  }

  private static String readMetadata() throws IOException {
    try (InputStream in =
        OutboxLegacyPropertiesTest.class.getClassLoader().getResourceAsStream(METADATA)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
