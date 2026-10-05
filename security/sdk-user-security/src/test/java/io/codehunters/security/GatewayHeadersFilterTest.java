package io.codehunters.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class GatewayHeadersFilterTest {

  private static final KeycloakPrincipal TOKEN_PRINCIPAL =
      new KeycloakPrincipal("user-1", "alice", null, null, null, null, null, null, "sid-1");

  private final GatewayHeadersFilter filter = new GatewayHeadersFilter();
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final MockFilterChain chain = new MockFilterChain();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void should_continue_when_no_authentication_and_no_identity_headers()
      throws ServletException, IOException {
    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void should_reject_when_identity_header_arrives_without_token()
      throws ServletException, IOException {
    request.addHeader("x-user-id", "user-1");

    filter.doFilter(request, response, chain);

    assertRejected();
  }

  @Test
  void should_reject_when_user_id_header_differs_from_token_subject()
      throws ServletException, IOException {
    authenticate("ROLE_USER");
    request.addHeader("x-user-id", "someone-else");

    filter.doFilter(request, response, chain);

    assertRejected();
  }

  @Test
  void should_reject_when_roles_header_grants_role_absent_from_token()
      throws ServletException, IOException {
    authenticate("ROLE_USER");
    request.addHeader("x-user-roles", "[\"user\",\"ADMIN\"]");

    filter.doFilter(request, response, chain);

    assertRejected();
  }

  @Test
  void should_reject_when_roles_header_is_malformed_json() throws ServletException, IOException {
    authenticate("ROLE_USER");
    request.addHeader("x-user-roles", "[\"user\"");

    filter.doFilter(request, response, chain);

    assertRejected();
  }

  @Test
  void should_continue_when_roles_header_is_subset_of_token_roles()
      throws ServletException, IOException {
    authenticate("ROLE_USER", "ROLE_ADMIN");
    request.addHeader("x-user-id", "user-1");
    request.addHeader("x-user-roles", "[\"user\"]");

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void should_accept_comma_separated_roles_header_when_subset_of_token_roles()
      throws ServletException, IOException {
    authenticate("ROLE_USER", "ROLE_ADMIN");
    request.addHeader("x-user-roles", "user, admin");

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void should_add_gateway_context_to_principal_and_keep_token_authorities()
      throws ServletException, IOException {
    authenticate("ROLE_USER");
    request.addHeader("x-geo-country", "CO");
    request.addHeader("x-geo-city", "Bogota");
    request.addHeader("x-geo-latitude", "4.71");
    request.addHeader("x-geo-longitude", "-74.07");
    request.addHeader("x-geo-ip", "10.0.0.1");
    request.addHeader("User-Agent", "test-agent");

    filter.doFilter(request, response, chain);

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication.getPrincipal())
        .isEqualTo(
            new KeycloakPrincipal(
                "user-1",
                "alice",
                "CO",
                "Bogota",
                "4.71",
                "-74.07",
                "10.0.0.1",
                "test-agent",
                "sid-1"));
    assertThat(authentication.getAuthorities())
        .extracting(Object::toString)
        .containsExactly("ROLE_USER");
  }

  private void authenticate(String... authorities) {
    List<SimpleGrantedAuthority> granted =
        java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(TOKEN_PRINCIPAL, "token", granted));
  }

  private void assertRejected() {
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).isEqualTo("application/problem+json");
    assertThat(chain.getRequest()).isNull();
  }
}
