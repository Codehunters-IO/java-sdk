package io.codehunters.weberrors.web;

import static io.codehunters.weberrors.web.WebMvcFixture.mvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.codehunters.weberrors.web.fixture.SampleController;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

class ProblemErrorControllerTest {

  private static final String ORIGINAL_PATH = "/orders/order-42";

  private final WebApplicationContextRunner runner =
      WebMvcFixture.runner()
          .withConfiguration(
              AutoConfigurations.of(
                  DispatcherServletAutoConfiguration.class, ErrorMvcAutoConfiguration.class));

  @Configuration(proxyBeanMethods = false)
  static class OwnErrorController {
    @Bean
    ErrorController ownErrorController() {
      return new ErrorController() {};
    }
  }

  @Test
  void should_replace_boot_basic_error_controller() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(ProblemErrorController.class);
          assertThat(context).doesNotHaveBean(BasicErrorController.class);
        });
  }

  @Test
  void should_back_off_when_the_application_declares_its_own_error_controller() {
    runner
        .withUserConfiguration(OwnErrorController.class)
        .run(context -> assertThat(context).doesNotHaveBean(ProblemErrorController.class));
  }

  @Test
  void should_answer_an_error_dispatch_with_a_problem_for_the_original_path() {
    runner.run(
        context ->
            mvc(context)
                .perform(
                    get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
                        .requestAttr(
                            RequestDispatcher.ERROR_REQUEST_URI,
                            ORIGINAL_PATH + ";jsessionid=SESSION42"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.detail").value("Forbidden"))
                .andExpect(jsonPath("$.instance").value(ORIGINAL_PATH)));
  }

  @Test
  void should_answer_500_when_the_error_dispatch_carries_no_status() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR")));
  }

  @Test
  void should_answer_in_problem_json_even_when_the_client_asks_for_html() {
    runner.run(
        context ->
            mvc(context)
                .perform(
                    get("/error")
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON)));
  }

  @Test
  void should_never_expose_the_exception_of_the_error_dispatch() {
    runner.run(
        context ->
            mvc(context)
                .perform(
                    get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(
                            RequestDispatcher.ERROR_EXCEPTION,
                            new IllegalStateException(SampleController.SECRET)))
                .andExpect(content().string(not(containsString(SampleController.SECRET)))));
  }
}
