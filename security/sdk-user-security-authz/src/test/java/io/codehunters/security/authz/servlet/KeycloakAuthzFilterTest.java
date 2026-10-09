package io.codehunters.security.authz.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.codehunters.security.authz.config.KeycloakPolicyEnforcerProperties;
import io.codehunters.security.authz.config.KeycloakPolicyEnforcerProperties.EnforcementMode;
import io.codehunters.security.authz.config.KeycloakPolicyEnforcerProperties.MethodMapping;
import io.codehunters.security.authz.config.KeycloakPolicyEnforcerProperties.PathMapping;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.authorization.client.AuthorizationDeniedException;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.resource.AuthorizationResource;
import org.keycloak.authorization.client.util.HttpResponseException;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class KeycloakAuthzFilterTest {

  private static final List<PathMapping> CATALOG =
      List.of(
          new PathMapping(
              "/api/v1/admin/**", "admin", List.of(new MethodMapping("GET", List.of("READ")))),
          new PathMapping(
              "/api/v1/admin/users",
              "usuarios",
              List.of(new MethodMapping("POST", List.of("CREATE")))));

  private final AuthzClient authzClient = mock(AuthzClient.class);
  private final AuthorizationResource authorization = mock(AuthorizationResource.class);
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final MockFilterChain chain = new MockFilterChain();

  @BeforeEach
  void setUp() {
    when(authzClient.authorization(anyString())).thenReturn(authorization);
  }

  @Test
  void should_pass_through_when_path_is_not_in_catalog() throws ServletException, IOException {
    filter(EnforcementMode.ENFORCING)
        .doFilter(request("GET", "/api/v1/public/info"), response, chain);

    assertAllowed();
    verifyNoInteractions(authzClient);
  }

  @Test
  void should_return_unauthorized_when_matched_path_has_no_bearer_token()
      throws ServletException, IOException {
    var request = new MockHttpServletRequest("POST", "/api/v1/admin/users");

    filter(EnforcementMode.ENFORCING).doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    verifyNoInteractions(authzClient);
  }

  @Test
  void should_continue_when_keycloak_grants_permission() throws ServletException, IOException {
    filter(EnforcementMode.ENFORCING)
        .doFilter(request("POST", "/api/v1/admin/users"), response, chain);

    assertAllowed();
  }

  @Test
  void should_ask_for_resource_of_most_specific_pattern() throws ServletException, IOException {
    filter(EnforcementMode.ENFORCING)
        .doFilter(request("POST", "/api/v1/admin/users"), response, chain);

    var captor = ArgumentCaptor.forClass(AuthorizationRequest.class);
    verify(authorization).authorize(captor.capture());
    assertThat(captor.getValue().getPermissions().getPermissions())
        .singleElement()
        .satisfies(permission -> assertThat(permission.getResourceId()).isEqualTo("usuarios"));
  }

  @Test
  void should_forbid_when_keycloak_denies_permission() throws ServletException, IOException {
    when(authorization.authorize(any())).thenThrow(denied());

    filter(EnforcementMode.ENFORCING)
        .doFilter(request("POST", "/api/v1/admin/users"), response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void should_forbid_method_missing_from_catalog_without_asking_keycloak()
      throws ServletException, IOException {
    filter(EnforcementMode.ENFORCING)
        .doFilter(request("DELETE", "/api/v1/admin/users"), response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    verifyNoInteractions(authzClient);
  }

  @Test
  void should_report_unavailable_when_keycloak_cannot_be_reached()
      throws ServletException, IOException {
    when(authorization.authorize(any())).thenThrow(new RuntimeException("Connection refused"));

    filter(EnforcementMode.ENFORCING)
        .doFilter(request("POST", "/api/v1/admin/users"), response, chain);

    assertThat(response.getStatus()).isEqualTo(503);
  }

  @Test
  void should_report_unavailable_when_client_cannot_be_created()
      throws ServletException, IOException {
    var filter =
        new KeycloakAuthzFilter(
            () -> {
              throw new RuntimeException("discovery failed");
            },
            properties(EnforcementMode.ENFORCING));

    filter.doFilter(request("POST", "/api/v1/admin/users"), response, chain);

    assertThat(response.getStatus()).isEqualTo(503);
  }

  @Test
  void should_allow_denied_request_when_permissive() throws ServletException, IOException {
    when(authorization.authorize(any())).thenThrow(denied());

    filter(EnforcementMode.PERMISSIVE)
        .doFilter(request("POST", "/api/v1/admin/users"), response, chain);

    assertAllowed();
  }

  @Test
  void should_allow_request_without_token_when_permissive() throws ServletException, IOException {
    var request = new MockHttpServletRequest("POST", "/api/v1/admin/users");

    filter(EnforcementMode.PERMISSIVE).doFilter(request, response, chain);

    assertAllowed();
  }

  @Test
  void should_forbid_when_keycloak_rejects_the_request_as_malformed()
      throws ServletException, IOException {
    when(authorization.authorize(any()))
        .thenThrow(
            new AuthorizationDeniedException(
                new HttpResponseException("invalid_resource", 400, "Bad Request", null)));

    filter(EnforcementMode.ENFORCING)
        .doFilter(request("POST", "/api/v1/admin/users"), response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void should_skip_enforcement_when_disabled() throws ServletException, IOException {
    filter(EnforcementMode.DISABLED)
        .doFilter(request("DELETE", "/api/v1/admin/users"), response, chain);

    assertAllowed();
    verifyNoInteractions(authzClient);
  }

  private KeycloakAuthzFilter filter(EnforcementMode mode) {
    return new KeycloakAuthzFilter(() -> authzClient, properties(mode));
  }

  private static KeycloakPolicyEnforcerProperties properties(EnforcementMode mode) {
    return new KeycloakPolicyEnforcerProperties(
        true,
        mode,
        "svc",
        "secret",
        CATALOG,
        Duration.ofSeconds(2),
        Duration.ofSeconds(3),
        Duration.ofSeconds(30));
  }

  private static MockHttpServletRequest request(String method, String path) {
    var request = new MockHttpServletRequest(method, path);
    request.addHeader("Authorization", "Bearer token-123");
    return request;
  }

  private static AuthorizationDeniedException denied() {
    return new AuthorizationDeniedException(
        new HttpResponseException("Unexpected response from server", 403, "Forbidden", null));
  }

  private void assertAllowed() {
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(chain.getRequest()).isNotNull();
  }
}
