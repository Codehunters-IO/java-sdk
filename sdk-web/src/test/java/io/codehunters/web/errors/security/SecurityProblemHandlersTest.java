package io.codehunters.web.errors.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.codehunters.web.errors.config.WebErrorsAutoConfiguration;
import io.codehunters.web.i18n.config.I18nAutoConfiguration;
import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.i18n.LocaleContextHolder;
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
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.instance").value("/orders/order-42")));
  }

  @AfterEach
  void resetLocale() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  void should_answer_401_in_the_x_locale_language_when_accept_language_names_another() {
    runner
        .withConfiguration(AutoConfigurations.of(I18nAutoConfiguration.class))
        .withUserConfiguration(SecureApplication.class, SecurityMessages.class)
        .run(
            context ->
                secured(context)
                    .perform(
                        get("/orders/order-42")
                            .header("X-Locale", "es")
                            .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value(SecurityMessages.SPANISH_UNAUTHORIZED)));
  }

  @Test
  void should_answer_403_in_the_x_locale_language_when_accept_language_names_another() {
    runner
        .withConfiguration(AutoConfigurations.of(I18nAutoConfiguration.class))
        .withUserConfiguration(SecureApplication.class, SecurityMessages.class)
        .run(
            context ->
                secured(context)
                    .perform(
                        get("/admin")
                            .header(HttpHeaders.AUTHORIZATION, basicCredentials())
                            .header("X-Locale", "es")
                            .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value(SecurityMessages.SPANISH_FORBIDDEN)));
  }

  @Test
  void should_answer_401_in_the_current_locale_when_no_locale_resolver_is_registered() {
    LocaleContextHolder.setLocale(Locale.of("es"));

    new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(WebErrorsAutoConfiguration.class))
        .withUserConfiguration(SecurityMessages.class)
        .run(
            context -> {
              var response = new MockHttpServletResponse();
              context
                  .getBean(ProblemAuthenticationEntryPoint.class)
                  .commence(
                      new MockHttpServletRequest("GET", "/orders"),
                      response,
                      new BadCredentialsException("bad"));
              assertThat(response.getContentAsString())
                  .contains(SecurityMessages.SPANISH_UNAUTHORIZED);
            });
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
