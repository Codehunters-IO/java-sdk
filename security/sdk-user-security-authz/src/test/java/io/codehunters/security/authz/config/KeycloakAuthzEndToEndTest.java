package io.codehunters.security.authz.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.codehunters.security.config.KeycloakSecurityAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * The official Keycloak enforcer running inside the sdk-user-security filter chain against a fake
 * realm: Spring Security authenticates first, Keycloak decides per path and method.
 */
class KeycloakAuthzEndToEndTest {

  private static final String AUTHZ = "codehunters.security.authz.";
  private static final String CUSTOM_HEADER = "X-Access-Token";

  private final FakeKeycloak keycloak = new FakeKeycloak();

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  KeycloakSecurityAutoConfiguration.class, KeycloakAuthzAutoConfiguration.class))
          .withUserConfiguration(WebApp.class)
          .withPropertyValues(
              "codehunters.security.url=" + keycloak.url(),
              "codehunters.security.realm=" + FakeKeycloak.REALM,
              "codehunters.security.audiences[0]=" + FakeKeycloak.CLIENT_ID,
              "codehunters.security.introspection.enabled=false",
              "codehunters.security.public-paths[0]=/public/**",
              "codehunters.security.excluded-paths[0]=/health",
              AUTHZ + "enabled=true",
              AUTHZ + "resource=" + FakeKeycloak.CLIENT_ID,
              AUTHZ + "client-secret=secret",
              AUTHZ + "paths[0].path=/api/users/*",
              AUTHZ + "paths[0].name=users",
              AUTHZ + "paths[0].methods[0].method=GET",
              AUTHZ + "paths[0].methods[0].scopes[0]=READ",
              AUTHZ + "paths[0].methods[1].method=POST",
              AUTHZ + "paths[0].methods[1].scopes[0]=CREATE",
              AUTHZ + "paths[1].path=/api/open/*",
              AUTHZ + "paths[1].name=users",
              AUTHZ + "paths[1].enforcement-mode=DISABLED",
              AUTHZ + "paths[2].path=/api/orders/{id}",
              AUTHZ + "paths[2].name=users",
              AUTHZ + "paths[2].methods[0].method=GET",
              AUTHZ + "paths[2].methods[0].scopes[0]=READ");

  @AfterEach
  void tearDown() {
    keycloak.close();
  }

  @Test
  void should_answer_401_without_asking_keycloak_when_request_has_no_token() {
    expect(get("/api/users/1"), status().isUnauthorized());

    assertThat(keycloak.umaCalls()).isZero();
  }

  @Test
  void should_answer_401_without_asking_keycloak_when_token_is_invalid() {
    expect(bearer(get("/api/users/1"), "not-a-jwt"), status().isUnauthorized());

    assertThat(keycloak.umaCalls()).isZero();
  }

  @Test
  void should_allow_request_when_keycloak_grants_scope_of_method() {
    keycloak.grant(FakeKeycloak.USERS_ID, "READ");

    expect(bearer(get("/api/users/1"), keycloak.token()), status().isOk(), "read 1");
  }

  @Test
  void should_forbid_request_when_keycloak_denies_scope_of_method() {
    keycloak.grant(FakeKeycloak.USERS_ID, "READ");

    expect(bearer(post("/api/users/1"), keycloak.token()), status().isForbidden());
    assertThat(keycloak.umaCalls()).isEqualTo(1);
  }

  @Test
  void should_forbid_authenticated_request_to_unmapped_path_when_enforcing() {
    expect(bearer(get("/api/other"), keycloak.token()), status().isForbidden());
  }

  @Test
  void should_pass_authenticated_request_to_unmapped_path_when_permissive() {
    runner
        .withPropertyValues(AUTHZ + "enforcement-mode=PERMISSIVE")
        .run(
            context ->
                mockMvc(context)
                    .perform(bearer(get("/api/other"), keycloak.token()))
                    .andExpect(status().isOk()));
  }

  @Test
  void should_not_ask_keycloak_when_path_enforcement_is_disabled() {
    expect(bearer(get("/api/open/1"), keycloak.token()), status().isOk(), "open 1");

    assertThat(keycloak.umaCalls()).isZero();
  }

  @Test
  void should_serve_public_path_without_token() {
    expect(get("/public/ping"), status().isOk(), "pong");
  }

  @Test
  void should_serve_excluded_path_without_token() {
    expect(get("/health"), status().isOk(), "up");
  }

  @Test
  void should_resolve_catalog_once_when_requests_repeat() {
    keycloak.grant(FakeKeycloak.USERS_ID, "READ");
    runner.run(
        context -> {
          MockMvc mvc = mockMvc(context);
          mvc.perform(bearer(get("/api/users/1"), keycloak.token())).andExpect(status().isOk());
          int lookups = keycloak.resourceLookups();

          mvc.perform(bearer(get("/api/users/1"), keycloak.token())).andExpect(status().isOk());

          assertThat(keycloak.resourceLookups()).isEqualTo(lookups);
        });
  }

  @Test
  void should_enforce_token_sent_as_query_parameter_when_application_resolver_allows_it() {
    runner
        .withBean(BearerTokenResolver.class, KeycloakAuthzEndToEndTest::queryParameterResolver)
        .run(
            context ->
                mockMvc(context)
                    .perform(get("/api/users/1").queryParam("access_token", keycloak.token()))
                    .andExpect(status().isForbidden()));
  }

  @Test
  void should_allow_token_sent_as_query_parameter_when_keycloak_grants_scope() {
    keycloak.grant(FakeKeycloak.USERS_ID, "READ");
    runner
        .withBean(BearerTokenResolver.class, KeycloakAuthzEndToEndTest::queryParameterResolver)
        .run(
            context ->
                mockMvc(context)
                    .perform(get("/api/users/1").queryParam("access_token", keycloak.token()))
                    .andExpect(status().isOk())
                    .andExpect(content().string("read 1")));
  }

  @Test
  void should_enforce_token_sent_in_custom_header_when_application_resolver_reads_it() {
    runner
        .withBean(BearerTokenResolver.class, KeycloakAuthzEndToEndTest::customHeaderResolver)
        .run(
            context ->
                mockMvc(context)
                    .perform(
                        get("/api/users/1").header(CUSTOM_HEADER, "Bearer " + keycloak.token()))
                    .andExpect(status().isForbidden()));
  }

  @Test
  void should_forbid_mapped_path_when_permissive() {
    runner
        .withPropertyValues(AUTHZ + "enforcement-mode=PERMISSIVE")
        .run(
            context ->
                mockMvc(context)
                    .perform(bearer(get("/api/orders/1"), keycloak.token()))
                    .andExpect(status().isForbidden()));
  }

  @Test
  void should_enforce_percent_encoded_path_like_its_decoded_form_when_permissive() {
    runner
        .withPropertyValues(AUTHZ + "enforcement-mode=PERMISSIVE")
        .run(
            context ->
                mockMvc(context)
                    .perform(bearer(get(URI.create("/api/%6Frders/1")), keycloak.token()))
                    .andExpect(status().isForbidden()));
  }

  @Test
  void should_count_failed_calls_to_keycloak_when_a_meter_registry_exists() {
    keycloak.grant(FakeKeycloak.USERS_ID, "READ");
    runner
        .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .run(
            context -> {
              MockMvc mvc = mockMvc(context);
              mvc.perform(bearer(get("/api/users/1"), keycloak.token())).andExpect(status().isOk());
              keycloak.close();

              mvc.perform(bearer(post("/api/users/1"), keycloak.token()))
                  .andExpect(status().isForbidden());

              assertThat(failures(context.getBean(MeterRegistry.class), "call")).isPositive();
            });
  }

  private static double failures(MeterRegistry registry, String stage) {
    return registry
        .get("codehunters.authz.enforcer.failures")
        .tag("stage", stage)
        .counter()
        .count();
  }

  private static BearerTokenResolver queryParameterResolver() {
    var resolver = new DefaultBearerTokenResolver();
    resolver.setAllowUriQueryParameter(true);
    return resolver;
  }

  private static BearerTokenResolver customHeaderResolver() {
    var resolver = new DefaultBearerTokenResolver();
    resolver.setBearerTokenHeaderName(CUSTOM_HEADER);
    return resolver;
  }

  private void expect(MockHttpServletRequestBuilder request, ResultMatcher status) {
    runner.run(context -> mockMvc(context).perform(request).andExpect(status));
  }

  private void expect(MockHttpServletRequestBuilder request, ResultMatcher status, String body) {
    runner.run(
        context ->
            mockMvc(context).perform(request).andExpect(status).andExpect(content().string(body)));
  }

  private static MockMvc mockMvc(WebApplicationContext context) {
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

    @GetMapping("/api/users/{id}")
    String read(@PathVariable String id) {
      return "read " + id;
    }

    @PostMapping("/api/users/{id}")
    String create(@PathVariable String id) {
      return "create " + id;
    }

    @GetMapping("/api/orders/{id}")
    String order(@PathVariable String id) {
      return "order " + id;
    }

    @GetMapping("/api/other")
    String other() {
      return "other";
    }

    @GetMapping("/api/open/{id}")
    String open(@PathVariable String id) {
      return "open " + id;
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
