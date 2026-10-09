package io.codehunters.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class KeycloakSecurityUtilsTest {

  private static final String USER_ID = "user-456";
  private static final String USERNAME = "jane.doe";
  private static final String COUNTRY = "ES";
  private static final String CITY = "Madrid";
  private static final String LATITUDE = "40.4";
  private static final String LONGITUDE = "-3.7";
  private static final String IP = "10.0.0.1";
  private static final String USER_AGENT = "agent/1.0";
  private static final String SESSION_ID = "sess-789";

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void should_returnPrincipal_when_authenticated() {
    // Given
    setAuthentication(USER_ID, USERNAME, List.of());

    // When
    var result = KeycloakSecurityUtils.getCurrentUser();

    // Then
    assertTrue(result.isPresent());
    assertEquals(USER_ID, result.get().userId());
    assertEquals(USERNAME, result.get().username());
  }

  @Test
  void should_returnEmpty_when_noAuthentication() {
    // Given — no authentication set

    // When
    var result = KeycloakSecurityUtils.getCurrentUser();

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void should_returnEmpty_when_principalNotKeycloak() {
    // Given
    var auth = new UsernamePasswordAuthenticationToken("plain-string-principal", null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);

    // When
    var result = KeycloakSecurityUtils.getCurrentUser();

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void should_returnUserId_when_authenticated() {
    // Given
    setAuthentication(USER_ID, USERNAME, List.of());

    // When
    var result = KeycloakSecurityUtils.getCurrentUserId();

    // Then
    assertTrue(result.isPresent());
    assertEquals(USER_ID, result.get());
  }

  @Test
  void should_returnRoles_when_authenticated() {
    // Given
    setAuthentication(
        USER_ID,
        USERNAME,
        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER")));

    // When
    List<String> roles = KeycloakSecurityUtils.getCurrentRoles();

    // Then
    assertEquals(2, roles.size());
    assertTrue(roles.contains("ROLE_ADMIN"));
    assertTrue(roles.contains("ROLE_USER"));
  }

  @Test
  void should_hasRole_withAndWithoutPrefix() {
    // Given
    setAuthentication(USER_ID, USERNAME, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    // When / Then
    assertTrue(KeycloakSecurityUtils.hasRole("ADMIN"));
    assertTrue(KeycloakSecurityUtils.hasRole("ROLE_ADMIN"));
    assertFalse(KeycloakSecurityUtils.hasRole("USER"));
  }

  @Test
  void should_returnUsername_when_authenticated() {
    // Given
    setAuthentication(USER_ID, USERNAME, List.of());

    // When
    var result = KeycloakSecurityUtils.getCurrentUsername();

    // Then
    assertTrue(result.isPresent());
    assertEquals(USERNAME, result.get());
  }

  @Test
  void should_returnEmptyUsername_when_noAuthentication() {
    // Given — no authentication set

    // When
    var result = KeycloakSecurityUtils.getCurrentUsername();

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void should_returnCountry_when_authenticated() {
    // Given
    setAuthentication(USER_ID, USERNAME, List.of());

    // When
    var result = KeycloakSecurityUtils.getCurrentCountry();

    // Then
    assertTrue(result.isPresent());
    assertEquals(COUNTRY, result.get());
  }

  @Test
  void should_returnEmptyCountry_when_noAuthentication() {
    // When
    var result = KeycloakSecurityUtils.getCurrentCountry();

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void should_returnCity_when_authenticated() {
    // Given
    setAuthentication(USER_ID, USERNAME, List.of());

    // When
    var result = KeycloakSecurityUtils.getCurrentCity();

    // Then
    assertTrue(result.isPresent());
    assertEquals(CITY, result.get());
  }

  @Test
  void should_returnEmptyCity_when_noAuthentication() {
    // When
    var result = KeycloakSecurityUtils.getCurrentCity();

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void should_returnLatitude_when_authenticated() {
    // Given
    setAuthentication(USER_ID, USERNAME, List.of());

    // When
    var result = KeycloakSecurityUtils.getCurrentLatitude();

    // Then
    assertTrue(result.isPresent());
    assertEquals(LATITUDE, result.get());
  }

  @Test
  void should_returnEmptyLatitude_when_noAuthentication() {
    // When
    var result = KeycloakSecurityUtils.getCurrentLatitude();

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void should_returnLongitude_when_authenticated() {
    // Given
    setAuthentication(USER_ID, USERNAME, List.of());

    // When
    var result = KeycloakSecurityUtils.getCurrentLongitude();

    // Then
    assertTrue(result.isPresent());
    assertEquals(LONGITUDE, result.get());
  }

  @Test
  void should_returnEmptyLongitude_when_noAuthentication() {
    // When
    var result = KeycloakSecurityUtils.getCurrentLongitude();

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void should_returnIp_when_authenticated() {
    // Given
    setAuthentication(USER_ID, USERNAME, List.of());

    // When
    var result = KeycloakSecurityUtils.getCurrentIp();

    // Then
    assertTrue(result.isPresent());
    assertEquals(IP, result.get());
  }

  @Test
  void should_returnEmptyIp_when_noAuthentication() {
    // When
    var result = KeycloakSecurityUtils.getCurrentIp();

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void should_returnSessionId_when_authenticated() {
    // Given
    setAuthentication(USER_ID, USERNAME, List.of());

    // When
    var result = KeycloakSecurityUtils.getCurrentSessionId();

    // Then
    assertTrue(result.isPresent());
    assertEquals(SESSION_ID, result.get());
  }

  @Test
  void should_returnEmptySessionId_when_noAuthentication() {
    // Given — no authentication set

    // When
    var result = KeycloakSecurityUtils.getCurrentSessionId();

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void should_returnGatewayContext_when_authenticated() {
    // Given
    setAuthentication(USER_ID, USERNAME, List.of());

    // When
    var result = KeycloakSecurityUtils.getGatewayContext();

    // Then
    assertEquals(
        Optional.of(new GatewayContext(COUNTRY, CITY, LATITUDE, LONGITUDE, IP, USER_AGENT)),
        result);
  }

  @Test
  void should_returnEmptyGatewayContext_when_noAuthentication() {
    // When
    var result = KeycloakSecurityUtils.getGatewayContext();

    // Then
    assertTrue(result.isEmpty());
  }

  @Test
  void should_returnEmptyRoles_when_noAuthentication() {
    // Given — no authentication set

    // When
    List<String> roles = KeycloakSecurityUtils.getCurrentRoles();

    // Then
    assertTrue(roles.isEmpty());
  }

  private void setAuthentication(
      String userId, String username, List<SimpleGrantedAuthority> authorities) {
    var principal =
        new KeycloakPrincipal(
            userId, username, COUNTRY, CITY, LATITUDE, LONGITUDE, IP, USER_AGENT, SESSION_ID);
    var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }
}
