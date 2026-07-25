package io.codehunters.sdk.security.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.resource.AuthorizationResource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class KeycloakAuthzFilterTest {

  private AuthzClient authzClient;
  private AuthorizationResource authorizationResource;
  private KeycloakAuthzFilter filter;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private MockFilterChain filterChain;

  @BeforeEach
  void setUp() {
    authzClient = mock(AuthzClient.class);
    authorizationResource = mock(AuthorizationResource.class);
    when(authzClient.authorization(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(authorizationResource);

    var props = new KeycloakPolicyEnforcerProperties();
    var pathMapping = new KeycloakPolicyEnforcerProperties.PathMapping();
    pathMapping.setPath("/api/v1/admin/users");
    pathMapping.setName("usuarios");
    var methodMapping = new KeycloakPolicyEnforcerProperties.MethodMapping();
    methodMapping.setMethod("POST");
    methodMapping.setScopes(List.of("CREATE"));
    pathMapping.setMethods(List.of(methodMapping));
    props.setPaths(List.of(pathMapping));

    filter = new KeycloakAuthzFilter(authzClient, props);
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    filterChain = new MockFilterChain();
  }

  @Test
  void should_passThrough_when_pathNotMatched() throws ServletException, IOException {
    request.setRequestURI("/api/v1/public/info");
    request.setMethod("GET");

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(200);
    verifyNoInteractions(authzClient);
  }

  @Test
  void should_returnUnauthorized_when_matchedPathHasNoBearerToken()
      throws ServletException, IOException {
    request.setRequestURI("/api/v1/admin/users");
    request.setMethod("POST");

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(401);
    verifyNoInteractions(authzClient);
  }

  @Test
  void should_continueChain_when_authorizeSucceeds() throws ServletException, IOException {
    request.setRequestURI("/api/v1/admin/users");
    request.setMethod("POST");
    request.addHeader("Authorization", "Bearer token-123");

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(200);
    verify(authorizationResource).authorize(any());
  }

  @Test
  void should_returnForbidden_when_authorizeThrows() throws ServletException, IOException {
    request.setRequestURI("/api/v1/admin/users");
    request.setMethod("POST");
    request.addHeader("Authorization", "Bearer token-123");
    when(authorizationResource.authorize(any())).thenThrow(new RuntimeException("denied"));

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(403);
  }
}
