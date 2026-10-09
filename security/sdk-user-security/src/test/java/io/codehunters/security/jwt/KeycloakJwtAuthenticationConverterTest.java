package io.codehunters.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.security.KeycloakPrincipal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class KeycloakJwtAuthenticationConverterTest {

  private static final Locale DEFAULT_LOCALE = Locale.getDefault();

  private final KeycloakJwtAuthenticationConverter converter =
      new KeycloakJwtAuthenticationConverter(List.of("svc"));

  @AfterEach
  void restoreLocale() {
    Locale.setDefault(DEFAULT_LOCALE);
  }

  @Test
  void should_build_principal_from_token_claims() {
    Authentication authentication = converter.convert(jwt(Map.of()));

    assertThat(authentication.isAuthenticated()).isTrue();
    assertThat(authentication.getPrincipal())
        .isEqualTo(
            new KeycloakPrincipal("user-1", "alice", null, null, null, null, null, null, "sid-1"));
  }

  @Test
  void should_grant_realm_roles_when_present() {
    Authentication authentication =
        converter.convert(jwt(Map.of("realm_access", Map.of("roles", List.of("user", "admin")))));

    assertThat(authorities(authentication)).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
  }

  @Test
  void should_grant_client_roles_only_for_configured_clients() {
    Authentication authentication =
        converter.convert(
            jwt(
                Map.of(
                    "resource_access",
                    Map.of(
                        "svc", Map.of("roles", List.of("editor")),
                        "other", Map.of("roles", List.of("owner"))))));

    assertThat(authorities(authentication)).containsExactly("ROLE_EDITOR");
  }

  @Test
  void should_grant_no_roles_when_token_has_none() {
    assertThat(authorities(converter.convert(jwt(Map.of())))).isEmpty();
  }

  @Test
  void should_upper_case_roles_with_root_locale_when_default_locale_is_turkish() {
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));

    Authentication authentication =
        converter.convert(jwt(Map.of("realm_access", Map.of("roles", List.of("admin")))));

    assertThat(authorities(authentication)).containsExactly("ROLE_ADMIN");
  }

  @Test
  void should_reject_a_token_without_a_subject_as_an_invalid_bearer_token() {
    var withoutSubject =
        Jwt.withTokenValue("token").header("alg", "RS256").claim("sid", "sid-1").build();

    assertThatThrownBy(() -> converter.convert(withoutSubject))
        .isInstanceOf(InvalidBearerTokenException.class)
        .hasMessage("The token has no subject");
  }

  private static Jwt jwt(Map<String, Object> extraClaims) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("user-1")
        .claim("preferred_username", "alice")
        .claim("sid", "sid-1")
        .claims(claims -> claims.putAll(extraClaims))
        .build();
  }

  private static List<String> authorities(Authentication authentication) {
    return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
  }
}
