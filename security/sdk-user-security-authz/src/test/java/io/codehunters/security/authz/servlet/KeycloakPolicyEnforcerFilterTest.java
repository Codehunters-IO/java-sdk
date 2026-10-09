package io.codehunters.security.authz.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.THROWABLE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.codehunters.security.KeycloakAuthenticationToken;
import io.codehunters.security.KeycloakPrincipal;
import jakarta.servlet.DispatcherType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.AuthorizationContext;
import org.keycloak.adapters.authorization.PolicyEnforcer;
import org.keycloak.adapters.authorization.spi.HttpRequest;
import org.keycloak.adapters.authorization.spi.HttpResponse;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
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
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

class KeycloakPolicyEnforcerFilterTest {

  // An unsigned JWT: Keycloak's enforcer parses the token it is given, so it must be one.
  private static final String JWT_TOKEN = "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ1c2VyLTEifQ.c2ln";
  private static final String RESOURCE = "orders-service";
  private static final String HEADER_TOKEN = "header-token";
  private static final Duration BACKOFF = Duration.ofSeconds(10);
  private static final Duration SHORT_WAIT = Duration.ofMillis(50);

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
  void should_forbid_when_another_mechanism_authenticated_the_request() throws Exception {
    authenticate(basicAuthentication());
    request.addHeader("Authorization", "Basic YWN0dWF0b3I6c2VjcmV0");

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(chain.getRequest()).isNull();
    assertThat(filter.attempts()).isZero();
  }

  @Test
  void should_forbid_when_basic_auth_authenticated_the_request_despite_a_bearer_header()
      throws Exception {
    authenticate(basicAuthentication());
    request.addHeader("Authorization", "Bearer " + HEADER_TOKEN);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(filter.attempts()).isZero();
  }

  @Test
  void should_leave_request_alone_when_its_path_is_skipped() throws Exception {
    var skipping =
        new ScriptedEnforcerFilter(
            KeycloakPolicyEnforcerFilter.Options.defaults()
                .withSkipPaths(PathPatternRequestMatcher.withDefaults().matcher("/actuator/**")));
    authenticate(basicAuthentication());
    request.setRequestURI("/actuator/health");

    skipping.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
    assertThat(skipping.attempts()).isZero();
  }

  @Test
  void should_forbid_when_a_custom_authentication_carries_the_token_only_as_credentials()
      throws Exception {
    authenticate(sdkAuthenticationHolding(jwt()));

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(filter.attempts()).isZero();
  }

  @Test
  void should_enforce_forward_dispatch() throws Exception {
    authenticate(jwtAuthentication());
    request.setDispatcherType(DispatcherType.FORWARD);

    filter.doFilter(request, response, chain);

    assertThat(filter.attempts()).isEqualTo(1);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void should_enforce_forward_dispatch_to_a_skipped_path() throws Exception {
    var skipping =
        new ScriptedEnforcerFilter(
            KeycloakPolicyEnforcerFilter.Options.defaults()
                .withSkipPaths(PathPatternRequestMatcher.withDefaults().matcher("/actuator/**")));
    authenticate(jwtAuthentication());
    request.setRequestURI("/actuator/health");
    request.setDispatcherType(DispatcherType.FORWARD);

    skipping.doFilter(request, response, chain);

    assertThat(skipping.attempts()).isEqualTo(1);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void should_forbid_without_asking_keycloak_when_token_is_not_a_jwt() throws Exception {
    authenticate(keycloakAuthentication(jwtWithValue("opaque-token")));

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(filter.attempts()).isZero();
  }

  @Test
  void should_forbid_a_permission_token_issued_to_another_client() throws Exception {
    var scoped =
        new ScriptedEnforcerFilter(KeycloakPolicyEnforcerFilter.Options.defaults(), RESOURCE);
    authenticate(keycloakAuthentication(permissionToken("other-service")));

    scoped.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(scoped.attempts()).isZero();
  }

  @Test
  void should_enforce_a_permission_token_issued_to_this_resource() throws Exception {
    var scoped =
        new ScriptedEnforcerFilter(KeycloakPolicyEnforcerFilter.Options.defaults(), RESOURCE);
    authenticate(keycloakAuthentication(permissionToken(RESOURCE)));

    scoped.doFilter(request, response, chain);

    assertThat(scoped.attempts()).isEqualTo(1);
  }

  @Test
  void should_enforce_include_dispatch() throws Exception {
    authenticate(jwtAuthentication());
    request.setDispatcherType(DispatcherType.INCLUDE);

    filter.doFilter(request, response, chain);

    assertThat(filter.attempts()).isEqualTo(1);
    assertThat(chain.getRequest()).isNull();
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
  void should_forbid_when_authentication_holds_no_validated_token_despite_a_bearer_header()
      throws Exception {
    authenticate(sdkAuthenticationWithErasedCredentials());
    request.addHeader("Authorization", "Bearer " + HEADER_TOKEN);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(filter.attempts()).isZero();
  }

  @Test
  void should_give_keycloak_the_validated_token_when_request_carries_another() throws Exception {
    request.addHeader("Authorization", "Bearer " + HEADER_TOKEN);

    HttpRequest seen = enforceGranted(keycloakAuthentication(jwt()));

    assertThat(seen.getPrincipal().getRawToken()).isEqualTo(JWT_TOKEN);
    assertThat(seen.getHeader("Authorization")).isEqualTo("Bearer " + JWT_TOKEN);
  }

  @Test
  void should_forbid_a_token_outside_the_bearer_charset() throws Exception {
    authenticate(keycloakAuthentication(jwtWithValue("abc\r\nX-Injected: 1")));

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(filter.attempts()).isZero();
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
  void should_match_the_decoded_path() throws Exception {
    request.setRequestURI("/ctx/api/%6Frders/1");
    request.setContextPath("/ctx");

    HttpRequest seen = enforceGranted(jwtAuthentication());

    assertThat(seen.getRelativePath()).isEqualTo("/api/orders/1");
    assertThat(seen.getURI()).isEqualTo("/ctx/api/orders/1");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/api/open%2F1",
        "/api/open%2f1",
        "/api/%2E%2E/users/1",
        "/api/users;jsessionid=1/1",
        "/api/users%3B/1",
        "/api/a%5Cb",
        "/api/%2541",
        "/api/orders/%2A",
        "/api/orders/%2a",
        "/api/%7Bid%7D",
        "/api/a%3Fb"
      })
  void should_forbid_without_asking_keycloak_when_raw_path_is_ambiguous(String uri)
      throws Exception {
    authenticate(jwtAuthentication());
    request.setRequestURI(uri);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(filter.attempts()).isZero();
  }

  @Test
  void should_answer_403_when_keycloak_denies_without_writing_a_response() throws Exception {
    filter.buildWith(enforcerAnswering(false));
    authenticate(jwtAuthentication());

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
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

    assertThat(failures).singleElement(THROWABLE).hasMessageContaining("Keycloak unreachable");
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
    assertThat(failures).singleElement(THROWABLE).hasMessageContaining("lookup failed");
  }

  @Test
  void should_give_keycloak_the_token_of_a_keycloak_authentication() throws Exception {
    HttpRequest seen = enforceGranted(keycloakAuthentication(jwt()));

    assertThat(seen.getPrincipal().getRawToken()).isEqualTo(JWT_TOKEN);
  }

  @Test
  void should_answer_503_when_the_first_build_outlasts_the_wait() throws Exception {
    var waiting =
        new ScriptedEnforcerFilter(
            KeycloakPolicyEnforcerFilter.Options.defaults().withFirstBuildWait(SHORT_WAIT));
    waiting.holdNextBuild();
    var building = CompletableFuture.runAsync(() -> filterAuthenticatedRequest(waiting));
    waiting.awaitHeldBuild();
    authenticate(jwtAuthentication());

    waiting.doFilter(request, response, chain);
    waiting.releaseBuild();
    building.join();

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(waiting.attempts()).isEqualTo(1);
  }

  @Test
  void should_not_start_a_second_rebuild_while_a_slow_one_runs() throws Exception {
    authenticate(jwtAuthentication());
    filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    clock.advance(BACKOFF);
    filter.holdNextBuild();
    var rebuilding = CompletableFuture.runAsync(this::filterAuthenticatedRequest);
    filter.awaitHeldBuild();
    clock.advance(BACKOFF.multipliedBy(2));

    filter.doFilter(request, response, chain);
    filter.releaseBuild();
    rebuilding.join();

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(filter.attempts()).isEqualTo(2);
  }

  @Test
  void should_show_keycloak_other_headers_unchanged() throws Exception {
    request.addHeader("X-Tenant", "acme");

    HttpRequest seen = enforceGranted(jwtAuthentication());

    assertThat(seen.getHeader("X-Tenant")).isEqualTo("acme");
    assertThat(seen.getHeaders("X-Tenant")).containsExactly("acme");
  }

  @Test
  void should_keep_keycloaks_answer_when_enforcer_fails_after_answering() throws Exception {
    PolicyEnforcer enforcer = mock(PolicyEnforcer.class);
    when(enforcer.enforce(any(), any())).thenAnswer(KeycloakPolicyEnforcerFilterTest::denyThenFail);
    filter.buildWith(enforcer);
    authenticate(jwtAuthentication());

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void should_wait_for_the_first_build_and_reuse_its_enforcer() throws Exception {
    filter.buildWith(enforcerAnswering(true));
    filter.holdNextBuild();
    var building = CompletableFuture.runAsync(this::filterAuthenticatedRequest);
    filter.awaitHeldBuild();
    Thread waiting = Threads.start(this::filterAuthenticatedRequest);
    Threads.awaitBlocked(waiting);

    filter.releaseBuild();
    building.join();
    waiting.join();

    assertThat(filter.attempts()).isEqualTo(1);
  }

  @Test
  void should_answer_503_to_requests_that_waited_for_a_failed_first_build() throws Exception {
    filter.holdNextBuild();
    var building = CompletableFuture.runAsync(this::filterAuthenticatedRequest);
    filter.awaitHeldBuild();
    Thread waiting = Threads.start(this::filterAuthenticatedRequest);
    Threads.awaitBlocked(waiting);

    filter.releaseBuild();
    building.join();
    waiting.join();

    assertThat(filter.attempts()).isEqualTo(1);
  }

  @Test
  void should_reject_options_without_a_positive_retry_backoff() {
    var defaults = KeycloakPolicyEnforcerFilter.Options.defaults();

    assertThatThrownBy(() -> defaults.withRetryBackoff(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> defaults.withRetryBackoff(null))
        .isInstanceOf(IllegalArgumentException.class);
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
    filterAuthenticatedRequest(filter);
  }

  private static void filterAuthenticatedRequest(KeycloakPolicyEnforcerFilter target) {
    try {
      SecurityContextHolder.getContext().setAuthentication(jwtAuthentication());
      target.doFilter(
          new MockHttpServletRequest("GET", "/api/users/1"),
          new MockHttpServletResponse(),
          new MockFilterChain());
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

  private static Object denyThenFail(InvocationOnMock invocation) {
    invocation.<HttpResponse>getArgument(1).sendError(403);
    throw new IllegalStateException("failed after answering");
  }

  private static Authentication jwtAuthentication() {
    return new JwtAuthenticationToken(jwt(), AuthorityUtils.NO_AUTHORITIES);
  }

  private static Jwt jwt() {
    return jwtWithValue(JWT_TOKEN);
  }

  private static Jwt jwtWithValue(String value) {
    return Jwt.withTokenValue(value).header("alg", "none").claims(c -> c.putAll(claims())).build();
  }

  private static Jwt permissionToken(String audience) {
    return Jwt.withTokenValue(JWT_TOKEN)
        .header("alg", "none")
        .claim("sub", "user-1")
        .audience(List.of(audience))
        .claim("authorization", Map.of("permissions", List.of(Map.of("rsid", "orders"))))
        .build();
  }

  private static Authentication keycloakAuthentication(Jwt jwt) {
    return new KeycloakAuthenticationToken(jwt, sdkPrincipal(), List.of());
  }

  private static Authentication sdkAuthenticationHolding(Jwt jwt) {
    return UsernamePasswordAuthenticationToken.authenticated(sdkPrincipal(), jwt, List.of());
  }

  private static KeycloakPrincipal sdkPrincipal() {
    return new KeycloakPrincipal("user-1", "alice", null, null, null, null, null, null, null);
  }

  private static Map<String, Object> claims() {
    return Map.of("sub", "user-1");
  }

  // sdk-user-security's chain authenticates a KeycloakPrincipal; Spring Security erases the token
  // it kept as credentials once authentication succeeds.
  private static Authentication sdkAuthenticationWithErasedCredentials() {
    return UsernamePasswordAuthenticationToken.authenticated(sdkPrincipal(), null, List.of());
  }

  private static Authentication basicAuthentication() {
    return UsernamePasswordAuthenticationToken.authenticated(
        "actuator", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
  }

  private static void authenticate(Authentication authentication) {
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
