package io.codehunters.web.errors.servlet;

import static io.codehunters.web.errors.servlet.WebMvcFixture.mvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

@ExtendWith(OutputCaptureExtension.class)
class ClientInputHandlingTest {

  private final WebApplicationContextRunner runner = WebMvcFixture.runner();

  @Test
  void should_answer_400_with_the_exception_code_when_client_input_is_rejected() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/client-input"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(SampleController.CLIENT_INPUT_CODE))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/client-input")));
  }

  @Test
  void should_use_the_exception_message_as_detail_when_the_bundle_has_no_message_for_the_code() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/client-input"))
                .andExpect(jsonPath("$.detail").value(SampleController.CLIENT_INPUT_MESSAGE)));
  }

  @Test
  void should_localize_the_detail_when_the_bundle_has_a_message_for_the_code() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/localized-client-input"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(SampleController.LOCALIZED_CLIENT_INPUT_CODE))
                .andExpect(jsonPath("$.detail").value("The search cannot be run")));
  }

  @Test
  void should_localize_the_detail_in_the_language_the_client_asks_for() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/localized-client-input").header(HttpHeaders.ACCEPT_LANGUAGE, "es"))
                .andExpect(jsonPath("$.detail").value("La búsqueda no es válida")));
  }

  @Test
  void should_answer_400_with_the_code_when_the_client_input_exception_is_a_cause() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/wrapped-client-input"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(SampleController.CLIENT_INPUT_CODE))
                .andExpect(jsonPath("$.detail").value(SampleController.CLIENT_INPUT_MESSAGE)));
  }

  @Test
  void should_never_send_the_wrapper_message_when_the_client_input_exception_is_a_cause() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/wrapped-client-input"))
                .andExpect(content().string(not(containsString(SampleController.SECRET)))));
  }

  @Test
  void should_not_log_rejected_client_input_as_a_server_failure(CapturedOutput output) {
    runner.run(context -> mvc(context).perform(get("/wrapped-client-input")));

    assertThat(output).doesNotContain("Request failed");
  }

  @Test
  void should_keep_answering_a_plain_illegal_argument_with_500() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/illegal-argument"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString(SampleController.SECRET)))));
  }
}
