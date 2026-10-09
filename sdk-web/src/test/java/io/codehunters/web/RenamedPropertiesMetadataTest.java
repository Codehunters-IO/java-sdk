package io.codehunters.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RenamedPropertiesMetadataTest {

  private static final String METADATA = "/META-INF/additional-spring-configuration-metadata.json";

  @ParameterizedTest
  @CsvSource({
    "codehunters.web-errors.enabled, codehunters.web.errors.enabled",
    "codehunters.web-errors.type-base-uri, codehunters.web.errors.type-base-uri",
    "codehunters.web-errors.include-trace-id, codehunters.web.errors.include-trace-id",
    "codehunters.device.enabled, codehunters.web.device.enabled",
    "codehunters.device.trusted-proxies, codehunters.web.device.trusted-proxies",
    "codehunters.device.forwarding-header, codehunters.web.device.forwarding-header",
    "codehunters.device.user-agent.max-length, codehunters.web.device.user-agent.max-length",
    "codehunters.device.user-agent.cache-size, codehunters.web.device.user-agent.cache-size",
    "codehunters.i18n.enabled, codehunters.web.i18n.enabled",
    "codehunters.i18n.basename, spring.messages.basename",
    "codehunters.i18n.default-locale, spring.web.locale",
    "codehunters.i18n.supported-locales, codehunters.web.i18n.supported-locales",
    "codehunters.i18n.header-name, codehunters.web.i18n.header-name",
    "codehunters.i18n.encoding, spring.messages.encoding",
    "codehunters.i18n.fallback-to-system-locale, spring.messages.fallback-to-system-locale"
  })
  void should_flag_renamed_key_as_error_with_its_replacement_when_metadata_is_read(
      String oldKey, String newKey) throws IOException {
    JsonNode deprecation = properties().get(oldKey).get("deprecation");

    assertThat(deprecation.get("level").asString()).isEqualTo("error");
    assertThat(deprecation.get("replacement").asString()).isEqualTo(newKey);
  }

  private static Map<String, JsonNode> properties() throws IOException {
    try (InputStream metadata = RenamedPropertiesMetadataTest.class.getResourceAsStream(METADATA)) {
      JsonNode root = JsonMapper.builder().build().readTree(metadata);
      return StreamSupport.stream(root.get("properties").spliterator(), false)
          .collect(Collectors.toMap(node -> node.get("name").asString(), node -> node));
    }
  }
}
