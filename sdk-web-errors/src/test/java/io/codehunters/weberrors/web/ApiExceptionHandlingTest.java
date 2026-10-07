package io.codehunters.weberrors.web;

import static io.codehunters.weberrors.web.WebMvcFixture.mvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.codehunters.weberrors.web.fixture.ApplicationAdvice;
import io.codehunters.weberrors.web.fixture.SampleController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlingTest {

  private final WebApplicationContextRunner runner = WebMvcFixture.runner();

  @Test
  void should_answer_with_the_code_status_and_localized_detail_when_an_api_exception_is_thrown() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/orders/order-42"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Order order-42 was not found"))
                .andExpect(jsonPath("$.instance").value("/orders/order-42")));
  }

  @Test
  void should_omit_the_type_meaning_about_blank_when_no_base_uri_is_configured() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/orders/order-42"))
                .andExpect(jsonPath("$.type").doesNotExist()));
  }

  @Test
  void should_localize_the_detail_when_the_client_asks_for_spanish() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/orders/order-42").header(HttpHeaders.ACCEPT_LANGUAGE, "es"))
                .andExpect(jsonPath("$.detail").value("No existe el pedido order-42")));
  }

  @Test
  void should_fall_back_to_the_reason_phrase_when_no_message_is_defined_for_the_code() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/orders/order-42/payment"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_PAID"))
                .andExpect(jsonPath("$.detail").value("Conflict")));
  }

  @Test
  void should_build_the_type_from_the_base_uri_when_one_is_configured() {
    runner
        .withPropertyValues("codehunters.web-errors.type-base-uri=https://errors.example.com/")
        .run(
            context ->
                mvc(context)
                    .perform(get("/orders/order-42"))
                    .andExpect(
                        jsonPath("$.type").value("https://errors.example.com/order-not-found")));
  }

  @Test
  void should_answer_500_when_the_code_declares_a_success_status() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/out-of-range"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("OUT_OF_RANGE_STATUS")));
  }

  @Test
  void should_answer_500_when_the_code_declares_a_status_beyond_599() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/unknown-status"))
                .andExpect(status().isInternalServerError()));
  }

  @Test
  void should_log_a_server_side_api_exception_at_error_without_leaking_its_cause(
      CapturedOutput output) {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/server-side"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVER_SIDE_FAILURE"))
                .andExpect(content().string(not(containsString(SampleController.SECRET)))));

    assertThat(output).contains("ERROR", "SERVER_SIDE_FAILURE");
  }

  @Test
  void should_not_log_a_client_error_at_error_level(CapturedOutput output) {
    runner.run(
        context -> mvc(context).perform(get("/orders/order-42")).andExpect(status().isNotFound()));

    assertThat(output).doesNotContain("ORDER_NOT_FOUND");
  }

  @Test
  void should_let_the_application_advice_win_when_it_handles_the_same_exception() {
    runner
        .withUserConfiguration(ApplicationAdvice.class)
        .run(
            context ->
                mvc(context)
                    .perform(get("/orders/order-42"))
                    .andExpect(status().isIAmATeapot())
                    .andExpect(content().string("handled by the application")));
  }
}
