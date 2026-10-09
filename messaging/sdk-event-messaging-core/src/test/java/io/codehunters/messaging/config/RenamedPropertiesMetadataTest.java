package io.codehunters.messaging.config;

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
    "codehunters.outbox.claim.ttl-ms, codehunters.outbox.claim.ttl",
    "codehunters.outbox.backoff.base-delay-ms, codehunters.outbox.backoff.base-delay",
    "codehunters.outbox.backoff.max-delay-ms, codehunters.outbox.backoff.max-delay"
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
