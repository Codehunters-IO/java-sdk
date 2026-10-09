package io.codehunters.web.errors.servlet;

import static io.codehunters.web.errors.servlet.WebMvcFixture.mvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.codehunters.web.errors.servlet.fixture.SampleController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(OutputCaptureExtension.class)
class FrameworkExceptionHandlingTest {

  private static final String REJECTED_SEGMENT = "notanumber";
  private static final String UNREADABLE =
      "{\"name\":\"x\",\"quantity\":\"" + REJECTED_SEGMENT + "\"}";

  private final WebApplicationContextRunner runner = WebMvcFixture.runner();

  @Test
  void should_answer_404_as_a_problem_when_no_handler_matches() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Not Found")));
  }

  @Test
  void should_localize_framework_errors_through_the_web_errors_keys() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/missing").header(HttpHeaders.ACCEPT_LANGUAGE, "es"))
                .andExpect(jsonPath("$.detail").value("Recurso inexistente")));
  }

  @Test
  void should_answer_405_and_keep_the_allow_header_when_the_method_is_not_supported() {
    runner.run(
        context ->
            mvc(context)
                .perform(delete("/orders/order-42"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED")));
  }

  @Test
  void should_answer_415_when_the_content_type_is_not_supported() {
    runner.run(
        context ->
            mvc(context)
                .perform(post("/orders").contentType(MediaType.TEXT_PLAIN).content("order"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE")));
  }

  @Test
  void should_hide_the_rejected_value_when_a_path_variable_has_the_wrong_type() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/quantities/" + REJECTED_SEGMENT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value(not(containsString(REJECTED_SEGMENT)))));
  }

  @Test
  void should_not_echo_the_parser_message_when_the_body_is_unreadable() {
    runner.run(
        context ->
            mvc(context)
                .perform(
                    post("/orders").contentType(MediaType.APPLICATION_JSON).content(UNREADABLE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(content().string(not(containsString(REJECTED_SEGMENT))))
                .andExpect(content().string(not(containsString("jackson")))));
  }

  @Test
  void should_answer_400_when_a_required_parameter_is_missing() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST")));
  }

  @Test
  void should_answer_500_without_the_exception_message_when_nobody_maps_the_exception() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Internal Server Error"))
                .andExpect(content().string(not(containsString(SampleController.SECRET))))
                .andExpect(content().string(not(containsString("IllegalStateException")))));
  }

  @Test
  void should_log_an_unmapped_exception_at_error_when_answering_500(CapturedOutput output) {
    runner.run(context -> mvc(context).perform(get("/boom")));

    assertThat(output).contains("ERROR", "INTERNAL_ERROR", "IllegalStateException");
  }

  @Test
  void should_honour_the_response_status_annotation_without_its_message() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/teapot"))
                .andExpect(status().isIAmATeapot())
                .andExpect(jsonPath("$.code").value("I_AM_A_TEAPOT"))
                .andExpect(jsonPath("$.detail").value("I'm a teapot")));
  }

  @Test
  void should_use_the_annotated_reason_when_the_response_status_annotation_states_one() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/reasoned-teapot"))
                .andExpect(jsonPath("$.detail").value("Only tea here")));
  }

  @Test
  void should_keep_the_reason_of_a_response_status_exception_thrown_by_the_application() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/gone"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("GONE"))
                .andExpect(jsonPath("$.detail").value("Order archived")));
  }

  @Test
  void should_use_the_reason_phrase_when_a_response_status_exception_has_no_reason() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/gone-silently"))
                .andExpect(jsonPath("$.detail").value("Gone")));
  }

  @Test
  void should_rethrow_security_exceptions_so_spring_security_answers_them() {
    runner.run(
        context ->
            assertThatThrownBy(() -> mvc(context).perform(get("/denied")))
                .hasRootCauseInstanceOf(AccessDeniedException.class));
  }
}
