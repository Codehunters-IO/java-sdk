package io.codehunters.security.config;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.codehunters.security.CurrentPrincipal;
import io.codehunters.security.KeycloakPrincipal;
import io.codehunters.security.KeycloakSecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class KeycloakSecurityEndToEndTest {

  private final FakeKeycloak keycloak = new FakeKeycloak();

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(KeycloakSecurityAutoConfiguration.class))
          .withUserConfiguration(WebApp.class)
          .withPropertyValues(
              "codehunters.security.url=" + keycloak.url(),
              "codehunters.security.realm=" + FakeKeycloak.REALM,
              "codehunters.security.audiences[0]=" + FakeKeycloak.CLIENT_ID,
              "codehunters.security.public-paths[0]=/public/**",
              "codehunters.security.excluded-paths[0]=/health",
              "codehunters.security.introspection.client-id=" + FakeKeycloak.CLIENT_ID,
              "codehunters.security.introspection.client-secret=secret");

  @AfterEach
  void tearDown() {
    keycloak.close();
  }

  @Test
  void should_reject_protected_path_without_token() {
    expect(get("/api/me"), status().isUnauthorized());
  }

  @Test
  void should_authenticate_from_token_claims_when_token_is_valid() {
    expect(bearer(get("/api/me"), keycloak.token()), status().isOk(), "user-1:ROLE_USER");
  }

  @Test
  void should_reject_token_signed_with_unknown_key() {
    expect(bearer(get("/api/me"), keycloak.foreignToken()), status().isUnauthorized());
  }

  @Test
  void should_reject_hs256_token_signed_with_public_key() {
    expect(
        bearer(get("/api/me"), keycloak.hs256TokenSignedWithPublicKey()),
        status().isUnauthorized());
  }

  @Test
  void should_not_refetch_key_set_for_every_unknown_key_id() {
    runner.run(
        context -> {
          MockMvc mvc = mockMvc(context);
          mvc.perform(bearer(get("/api/me"), keycloak.tokenWithKeyId("unknown-1")))
              .andExpect(status().isUnauthorized());
          mvc.perform(bearer(get("/api/me"), keycloak.tokenWithKeyId("unknown-2")))
              .andExpect(status().isUnauthorized());
          mvc.perform(bearer(get("/api/me"), keycloak.tokenWithKeyId("unknown-3")))
              .andExpect(status().isUnauthorized());
          mvc.perform(bearer(get("/api/me"), keycloak.tokenWithKeyId("unknown-4")))
              .andExpect(status().isUnauthorized());
          mvc.perform(bearer(get("/api/me"), keycloak.tokenWithKeyId("unknown-5")))
              .andExpect(status().isUnauthorized());

          org.assertj.core.api.Assertions.assertThat(keycloak.jwksCalls()).isLessThanOrEqualTo(2);
        });
  }

  @Test
  void should_reject_unsigned_token() {
    expect(bearer(get("/api/me"), keycloak.unsignedToken()), status().isUnauthorized());
  }

  @Test
  void should_reject_expired_token() {
    String expired =
        keycloak.token(claims -> claims.expirationTime(Date.from(Instant.now().minusSeconds(600))));

    expect(bearer(get("/api/me"), expired), status().isUnauthorized());
  }

  @Test
  void should_reject_token_from_another_issuer() {
    String token = keycloak.token(claims -> claims.issuer("https://evil.example.com/realms/test"));

    expect(bearer(get("/api/me"), token), status().isUnauthorized());
  }

  @Test
  void should_reject_token_for_another_audience() {
    String token = keycloak.token(claims -> claims.audience("other-service"));

    expect(bearer(get("/api/me"), token), status().isUnauthorized());
  }

  @Test
  void should_reject_roles_header_escalation_when_token_lacks_role() {
    expect(
        bearer(get("/api/me"), keycloak.token()).header("x-user-roles", "[\"ADMIN\"]"),
        status().isUnauthorized());
  }

  @Test
  void should_reject_user_id_header_that_differs_from_token_subject() {
    expect(
        bearer(get("/api/me"), keycloak.token()).header("x-user-id", "someone-else"),
        status().isUnauthorized());
  }

  @Test
  void should_reject_token_of_revoked_session() {
    keycloak.sessionActive(false);

    expect(bearer(get("/api/me"), keycloak.token()), status().isUnauthorized());
  }

  @Test
  void should_reject_when_introspection_fails_and_fail_open_disabled() {
    keycloak.introspectionFailsWith(503);

    expect(bearer(get("/api/me"), keycloak.token()), status().isUnauthorized());
  }

  @Test
  void should_forbid_method_when_token_lacks_required_role() {
    expect(bearer(get("/api/admin"), keycloak.token()), status().isForbidden());
  }

  @Test
  void should_allow_method_when_token_has_required_role() {
    String admin =
        keycloak.token(
            claims -> claims.claim("realm_access", Map.of("roles", List.of("user", "admin"))));

    expect(bearer(get("/api/admin"), admin), status().isOk(), "admin");
  }

  @Test
  void should_serve_public_path_without_token() {
    expect(get("/public/ping"), status().isOk(), "pong");
  }

  @Test
  void should_skip_security_on_excluded_path_even_with_invalid_token() {
    expect(bearer(get("/health"), "not-a-jwt"), status().isOk(), "up");
  }

  @Test
  void should_report_user_id_as_remote_user_when_token_is_valid() {
    expect(bearer(get("/api/remote-user"), keycloak.token()), status().isOk(), "user-1");
  }

  @Test
  void should_match_authentication_name_against_path_user_id() {
    expect(bearer(get("/api/users/user-1"), keycloak.token()), status().isOk(), "user-1");
  }

  @Test
  void should_forbid_another_users_path_when_authentication_name_differs() {
    expect(bearer(get("/api/users/user-2"), keycloak.token()), status().isForbidden());
  }

  private void expect(MockHttpServletRequestBuilder request, ResultMatcher status) {
    runner.run(context -> mockMvc(context).perform(request).andExpect(status));
  }

  private void expect(MockHttpServletRequestBuilder request, ResultMatcher status, String body) {
    runner.run(
        context ->
            mockMvc(context).perform(request).andExpect(status).andExpect(content().string(body)));
  }

  private static MockMvc mockMvc(org.springframework.web.context.WebApplicationContext context) {
    return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private static MockHttpServletRequestBuilder bearer(
      MockHttpServletRequestBuilder request, String token) {
    return request.header("Authorization", "Bearer " + token);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  @Import(TestController.class)
  static class WebApp {}

  @RestController
  static class TestController {

    @GetMapping("/api/me")
    String me(@CurrentPrincipal KeycloakPrincipal principal) {
      return principal.userId() + ":" + String.join(",", KeycloakSecurityUtils.getCurrentRoles());
    }

    @GetMapping("/api/admin")
    @PreAuthorize("hasRole('ADMIN')")
    String admin() {
      return "admin";
    }

    @GetMapping("/api/remote-user")
    String remoteUser(HttpServletRequest request) {
      return request.getRemoteUser();
    }

    @GetMapping("/api/users/{id}")
    @PreAuthorize("authentication.name == #id")
    String user(@PathVariable String id) {
      return id;
    }

    @GetMapping("/public/ping")
    String ping() {
      return "pong";
    }

    @GetMapping("/health")
    String health() {
      return "up";
    }
  }
}
