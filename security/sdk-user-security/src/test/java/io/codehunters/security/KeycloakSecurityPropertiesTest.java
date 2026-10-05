package io.codehunters.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class KeycloakSecurityPropertiesTest {

  @Test
  void should_haveDefaultCorsValues() {
    // Given
    var properties = new KeycloakSecurityProperties();

    // When
    var cors = properties.getCors();

    // Then
    assertNotNull(cors);
    assertTrue(cors.getAllowedOrigins().isEmpty());
    assertEquals(
        List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"), cors.getAllowedMethods());
    assertEquals(List.of("*"), cors.getAllowedHeaders());
    assertFalse(cors.isAllowCredentials());
    assertEquals(3600L, cors.getMaxAge());
  }

  @Test
  void should_setAndGetProperties() {
    // Given
    var properties = new KeycloakSecurityProperties();
    var publicPaths = List.of("/api/public/**");
    var excludedPaths = List.of("/actuator/**");

    // When
    properties.setPublicPaths(publicPaths);
    properties.setExcludedPaths(excludedPaths);

    var cors = new KeycloakSecurityProperties.Cors();
    cors.setAllowedOrigins(List.of("http://localhost:3000"));
    cors.setAllowedMethods(List.of("GET", "POST"));
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    cors.setAllowCredentials(true);
    cors.setMaxAge(7200L);
    properties.setCors(cors);

    // Then
    assertEquals(publicPaths, properties.getPublicPaths());
    assertEquals(excludedPaths, properties.getExcludedPaths());
    assertEquals(List.of("http://localhost:3000"), properties.getCors().getAllowedOrigins());
    assertEquals(List.of("GET", "POST"), properties.getCors().getAllowedMethods());
    assertEquals(
        List.of("Authorization", "Content-Type"), properties.getCors().getAllowedHeaders());
    assertTrue(properties.getCors().isAllowCredentials());
    assertEquals(7200L, properties.getCors().getMaxAge());
  }

  @Test
  void should_deriveIntrospectionUri_fromUrlAndRealm() {
    var properties = new KeycloakSecurityProperties();
    properties.setUrl("https://kc/");
    properties.setRealm("vitxo");

    assertEquals(
        "https://kc/realms/vitxo/protocol/openid-connect/token/introspect",
        properties.getIntrospectionUri());
  }
}
