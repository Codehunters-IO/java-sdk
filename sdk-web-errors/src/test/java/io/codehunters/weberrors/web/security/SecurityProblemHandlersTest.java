package io.codehunters.weberrors.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.codehunters.weberrors.config.WebErrorsAutoConfiguration;
import io.codehunters.weberrors.web.ProblemAccessDeniedHandler;
import io.codehunters.weberrors.web.ProblemAuthenticationEntryPoint;
import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class SecurityProblemHandlersTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  WebErrorsAutoConfiguration.class,
                  WebMvcAutoConfiguration.class,
                  HttpMessageConvertersAutoConfiguration.class,
                  JacksonAutoConfiguration.class));

  @Test
  void should_answer_an_unauthenticated_request_with_a_problem_and_a_bearer_challenge() {
    runner
        .withUserConfiguration(SecureApplication.class)
        .run(
            context ->
                secured(context)
                    .perform(get("/orders/order-42"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.instance").value("/orders/order-42")));
  }

  @Test
  void should_answer_a_forbidden_request_with_a_problem() {
    runner
        .withUserConfiguration(SecureApplication.class)
        .run(
            context ->
                secured(context)
                    .perform(get("/admin").header(HttpHeaders.AUTHORIZATION, basicCredentials()))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("FORBIDDEN")));
  }

  @Test
  void should_register_no_security_handlers_without_spring_security() {
    runner
        .withClassLoader(new FilteredClassLoader(AuthenticationEntryPoint.class))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(ProblemAuthenticationEntryPoint.class);
              assertThat(context).doesNotHaveBean(ProblemAccessDeniedHandler.class);
            });
  }

  @Test
  void should_answer_401_without_a_bearer_challenge_when_the_resource_server_is_absent() {
    runner
        .withClassLoader(new FilteredClassLoader(BearerTokenAuthenticationEntryPoint.class))
        .run(
            context -> {
              var response = new MockHttpServletResponse();
              context
                  .getBean(ProblemAuthenticationEntryPoint.class)
                  .commence(
                      new MockHttpServletRequest("GET", "/orders"),
                      response,
                      new BadCredentialsException("bad"));
              assertThat(response.getStatus()).isEqualTo(401);
              assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
              assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
            });
  }

  @Test
  void should_leave_a_committed_response_alone() {
    runner.run(
        context -> {
          var response = new MockHttpServletResponse();
          response.setCommitted(true);
          context
              .getBean(ProblemAuthenticationEntryPoint.class)
              .commence(
                  new MockHttpServletRequest("GET", "/orders"),
                  response,
                  new BadCredentialsException("bad"));
          assertThat(response.getContentAsString()).isEmpty();
        });
  }

  private static MockMvc secured(WebApplicationContext context) {
    return MockMvcBuilders.webAppContextSetup(context)
        .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
        .build();
  }

  private static String basicCredentials() {
    String credentials = SecureApplication.USER + ":" + SecureApplication.PASSWORD;
    return "Basic "
        + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
  }
}
