package io.codehunters.security.authz.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.codehunters.security.KeycloakPrincipal;
import jakarta.servlet.DispatcherType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.AuthorizationContext;
import org.keycloak.adapters.authorization.PolicyEnforcer;
import org.keycloak.adapters.authorization.spi.HttpRequest;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class KeycloakPolicyEnforcerFilterTest {

  private static final String JWT_TOKEN = "jwt-token";
  private static final String HEADER_TOKEN = "header-token";
  private static final Duration BACKOFF = Duration.ofSeconds(10);

  private final MutableClock clock = new MutableClock();
  private final List<RuntimeException> failures = new CopyOnWriteArrayList<>();
  private final ScriptedEnforcerFilter filter =
      new ScriptedEnforcerFilter(
          KeycloakPolicyEnforcerFilter.Options.defaults()
              .withRetryBackoff(BACKOFF)
              .withClock(clock)
              .withFailureListener(failures::add));

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
    authenticate(jwtAuthentication());
    request.setDispatcherType(DispatcherType.ERROR);

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void should_leave_request_alone_when_another_chain_authenticated_it_with_basic_auth()
      throws Exception {
    authenticate(basicAuthentication());
    request.addHeader("Authorization", "Basic YWN0dWF0b3I6c2VjcmV0");

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
    assertThat(filter.attempts()).isZero();
  }

  @Test
  void should_leave_request_alone_when_basic_auth_authenticated_it_despite_a_bearer_header()
      throws Exception {
    authenticate(basicAuthentication());
    request.addHeader("Authorization", "Bearer " + HEADER_TOKEN);

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
    assertThat(filter.attempts()).isZero();
  }

  @Test
  void should_enforce_when_authentication_holds_a_jwt_and_request_has_no_bearer_header()
      throws Exception {
    authenticate(jwtAuthentication());

    filter.doFilter(request, response, chain);

    assertThat(filter.attempts()).isEqualTo(1);
    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void should_give_keycloak_the_token_of_the_authentication() throws Exception {
    HttpRequest seen = enforceGranted(jwtAuthentication());

    assertThat(seen.getPrincipal().getRawToken()).isEqualTo(JWT_TOKEN);
    assertThat(seen.getHeader("Authorization")).isEqualTo("Bearer " + JWT_TOKEN);
    assertThat(seen.getHeaders("Authorization")).containsExactly("Bearer " + JWT_TOKEN);
  }

  @Test
  void should_resolve_the_token_from_the_request_when_authentication_no_longer_holds_it()
      throws Exception {
    request.addHeader("Authorization", "Bearer " + HEADER_TOKEN);

    HttpRequest seen = enforceGranted(sdkAuthenticationWithErasedCredentials());

    assertThat(seen.getPrincipal().getRawToken()).isEqualTo(HEADER_TOKEN);
  }

  @Test
  void should_forbid_when_token_authentication_has_no_resolvable_token() throws Exception {
    authenticate(sdkAuthenticationWithErasedCredentials());

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(chain.getRequest()).isNull();
    assertThat(filter.attempts()).isZero();
  }

  @Test
  void should_match_the_decoded_path_without_path_parameters() throws Exception {
    request.setRequestURI("/ctx/api/%6Frders;jsessionid=1/1");
    request.setContextPath("/ctx");

    HttpRequest seen = enforceGranted(jwtAuthentication());

    assertThat(seen.getRelativePath()).isEqualTo("/api/orders/1");
    assertThat(seen.getURI()).isEqualTo("/ctx/api/orders/1");
  }

  @Test
  void should_continue_with_the_original_request_when_keycloak_grants() throws Exception {
    enforceGranted(jwtAuthentication());

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void should_stop_the_chain_when_keycloak_denies() throws Exception {
    filter.buildWith(enforcerAnswering(false));
    authenticate(jwtAuthentication());

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void should_answer_503_without_rebuilding_when_build_failed_within_retry_backoff()
      throws Exception {
    authenticate(jwtAuthentication());
    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    clock.advance(BACKOFF.minusSeconds(1));

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(filter.attempts()).isEqualTo(1);
  }

  @Test
  void should_rebuild_once_retry_backoff_elapsed() throws Exception {
    authenticate(jwtAuthentication());
    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    filter.buildWith(enforcerAnswering(true));
    clock.advance(BACKOFF);

    filter.doFilter(request, response, chain);

    assertThat(filter.attempts()).isEqualTo(2);
    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void should_answer_503_while_another_request_is_rebuilding() throws Exception {
    authenticate(jwtAuthentication());
    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    clock.advance(BACKOFF);
    filter.holdNextBuild();
    var rebuilding = CompletableFuture.runAsync(this::filterAuthenticatedRequest);
    filter.awaitHeldBuild();

    filter.doFilter(request, response, chain);
    filter.releaseBuild();
    rebuilding.join();

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(filter.attempts()).isEqualTo(2);
  }

  @Test
  void should_report_failed_build_to_failure_listener() throws Exception {
    authenticate(jwtAuthentication());

    filter.doFilter(request, response, chain);

    assertThat(failures).singleElement().hasMessageContaining("Keycloak unreachable");
  }

  @Test
  void should_answer_503_and_report_when_enforcer_fails_after_being_built() throws Exception {
    PolicyEnforcer enforcer = mock(PolicyEnforcer.class);
    when(enforcer.enforce(any(), any())).thenThrow(new IllegalStateException("lookup failed"));
    filter.buildWith(enforcer);
    authenticate(jwtAuthentication());

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(chain.getRequest()).isNull();
    assertThat(failures).singleElement().hasMessageContaining("lookup failed");
  }

  private HttpRequest enforceGranted(Authentication authentication) throws Exception {
    PolicyEnforcer enforcer = enforcerAnswering(true);
    filter.buildWith(enforcer);
    authenticate(authentication);

    filter.doFilter(request, response, chain);

    var seen = ArgumentCaptor.forClass(HttpRequest.class);
    verify(enforcer).enforce(seen.capture(), any());
    return seen.getValue();
  }

  private void filterAuthenticatedRequest() {
    try {
      SecurityContextHolder.getContext().setAuthentication(jwtAuthentication());
      filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private static PolicyEnforcer enforcerAnswering(boolean granted) {
    AuthorizationContext context = mock(AuthorizationContext.class);
    when(context.isGranted()).thenReturn(granted);
    PolicyEnforcer enforcer = mock(PolicyEnforcer.class);
    when(enforcer.enforce(any(), any())).thenReturn(context);
    return enforcer;
  }

  private static Authentication jwtAuthentication() {
    Jwt jwt =
        Jwt.withTokenValue(JWT_TOKEN).header("alg", "none").claims(c -> c.putAll(claims())).build();
    return new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES);
  }

  private static Map<String, Object> claims() {
    return Map.of("sub", "user-1");
  }

  // sdk-user-security's chain authenticates a KeycloakPrincipal; Spring Security erases the token
  // it kept as credentials once authentication succeeds.
  private static Authentication sdkAuthenticationWithErasedCredentials() {
    var principal =
        new KeycloakPrincipal("user-1", "alice", null, null, null, null, null, null, null);
    return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
  }

  private static Authentication basicAuthentication() {
    return UsernamePasswordAuthenticationToken.authenticated(
        "actuator", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
  }

  private static void authenticate(Authentication authentication) {
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
