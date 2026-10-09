package io.codehunters.security.authz.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.DispatcherType;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class KeycloakPolicyEnforcerFilterTest {

  // Nothing listens on port 1: any attempt to build the enforcer fails to reach Keycloak.
  private final KeycloakPolicyEnforcerFilter filter =
      new KeycloakPolicyEnforcerFilter(unreachableKeycloak(), null);

  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/1");
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final MockFilterChain chain = new MockFilterChain();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void should_leave_request_to_spring_security_when_it_has_no_authentication() throws Exception {
    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void should_leave_request_to_spring_security_when_authentication_is_anonymous() throws Exception {
    authenticate(
        new AnonymousAuthenticationToken(
            "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void should_not_enforce_again_when_request_is_an_error_dispatch() throws Exception {
    authenticate(new TestingAuthenticationToken("user-1", null, "ROLE_USER"));
    request.setDispatcherType(DispatcherType.ERROR);

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void should_answer_503_when_keycloak_is_unreachable() throws Exception {
    authenticate(new TestingAuthenticationToken("user-1", null, "ROLE_USER"));
    request.addHeader("Authorization", "Bearer token");

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(chain.getRequest()).isNull();
  }

  private static void authenticate(Authentication authentication) {
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private static PolicyEnforcerConfig unreachableKeycloak() {
    var config = new PolicyEnforcerConfig();
    config.setAuthServerUrl("http://127.0.0.1:1");
    config.setRealm("test");
    config.setResource("svc");
    config.setCredentials(Map.of("secret", "secret"));
    return config;
  }
}
