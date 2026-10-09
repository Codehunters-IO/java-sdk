package io.codehunters.security.config;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.codehunters.web.errors.config.WebErrorsAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class KeycloakSecurityProblemDetailsTest {

  private final FakeKeycloak keycloak = new FakeKeycloak();

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class,
                  WebErrorsAutoConfiguration.class,
                  KeycloakSecurityAutoConfiguration.class))
          .withUserConfiguration(KeycloakSecurityEndToEndTest.WebApp.class)
          .withPropertyValues(
              "codehunters.security.url=" + keycloak.url(),
              "codehunters.security.realm=" + FakeKeycloak.REALM,
              "codehunters.security.audiences[0]=" + FakeKeycloak.CLIENT_ID,
              "codehunters.security.introspection.client-id=" + FakeKeycloak.CLIENT_ID,
              "codehunters.security.introspection.client-secret=secret");

  @AfterEach
  void tearDown() {
    keycloak.close();
  }

  @Test
  void should_answer_problem_detail_with_bearer_challenge_when_token_is_missing() {
    runner.run(
        context ->
            mockMvc(context)
                .perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").exists()));
  }

  @Test
  void should_answer_problem_detail_when_role_is_missing() {
    runner.run(
        context ->
            mockMvc(context)
                .perform(get("/api/admin").header("Authorization", "Bearer " + keycloak.token()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").exists()));
  }

  @Test
  void should_keep_plain_bearer_challenge_when_web_errors_is_absent() {
    new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(KeycloakSecurityAutoConfiguration.class))
        .withUserConfiguration(KeycloakSecurityEndToEndTest.WebApp.class)
        .withPropertyValues(
            "codehunters.security.url=" + keycloak.url(),
            "codehunters.security.realm=" + FakeKeycloak.REALM,
            "codehunters.security.audiences[0]=" + FakeKeycloak.CLIENT_ID,
            "codehunters.security.introspection.client-id=" + FakeKeycloak.CLIENT_ID,
            "codehunters.security.introspection.client-secret=secret")
        .run(
            context ->
                mockMvc(context)
                    .perform(get("/api/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                    .andExpect(content().string("")));
  }

  private static MockMvc mockMvc(WebApplicationContext context) {
    return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }
}
