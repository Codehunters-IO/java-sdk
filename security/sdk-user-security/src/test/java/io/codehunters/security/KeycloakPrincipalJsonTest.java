package io.codehunters.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class KeycloakPrincipalJsonTest {

  private static final String USER_ID = "user-1";

  private final JsonMapper mapper =
      JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

  private final KeycloakPrincipal principal =
      new KeycloakPrincipal(
          USER_ID,
          "alice",
          "CO",
          "Bogota",
          "4.7110",
          "-74.0721",
          "203.0.113.7",
          "agent/1.0",
          "sid-1");

  @Test
  void should_write_the_same_properties_as_4_0_when_written_as_json() {
    Map<String, Object> json =
        mapper.readValue(mapper.writeValueAsString(principal), new TypeReference<>() {});

    assertThat(json)
        .containsOnlyKeys(
            "userId",
            "username",
            "country",
            "city",
            "latitude",
            "longitude",
            "ip",
            "userAgent",
            "sessionId");
  }

  @Test
  void should_read_back_what_it_wrote_when_unknown_properties_fail() {
    var json = mapper.writeValueAsString(principal);

    assertThat(mapper.readValue(json, KeycloakPrincipal.class)).isEqualTo(principal);
  }

  @Test
  void should_not_add_a_gateway_context_property_when_written_as_json() {
    Map<String, Object> json =
        mapper.readValue(mapper.writeValueAsString(principal), new TypeReference<>() {});

    assertThat(json).doesNotContainKey("gatewayContext");
  }
}
