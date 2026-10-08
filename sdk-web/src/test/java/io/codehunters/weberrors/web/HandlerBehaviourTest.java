package io.codehunters.weberrors.web;

import static io.codehunters.weberrors.web.WebMvcFixture.mvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.codehunters.weberrors.web.fixture.SampleController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.util.WebUtils;

@ExtendWith(OutputCaptureExtension.class)
class HandlerBehaviourTest {

  private static final String STACK_FRAME = "\tat ";

  private final WebApplicationContextRunner runner = WebMvcFixture.runner();

  @Test
  void should_expose_the_exception_to_observability_when_answering_a_server_error() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/boom"))
                .andExpect(
                    request()
                        .attribute(
                            WebUtils.ERROR_EXCEPTION_ATTRIBUTE,
                            instanceOf(IllegalStateException.class))));
  }

  @Test
  void should_not_flag_a_client_error_to_observability() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/orders/order-42"))
                .andExpect(request().attribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, nullValue())));
  }

  @Test
  void should_leave_a_committed_response_untouched() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/committed"))
                .andExpect(status().isOk())
                .andExpect(content().string("partial")));
  }

  @Test
  void should_warn_once_without_stack_or_message_when_the_response_is_committed(
      CapturedOutput output) {
    runner.run(context -> mvc(context).perform(get("/committed")));

    assertThat(output)
        .contains("WARN", "response already committed")
        .doesNotContain(SampleController.SECRET, "Ignoring", STACK_FRAME);
  }

  @Test
  void should_not_report_a_disconnected_client_as_a_server_error(CapturedOutput output) {
    runner.run(context -> mvc(context).perform(get("/disconnected")));

    assertThat(output).doesNotContain("Request failed");
  }

  @Test
  void should_answer_503_when_an_async_request_times_out() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/slow"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE")));
  }

  @Test
  void should_warn_without_stack_when_an_async_request_times_out(CapturedOutput output) {
    runner.run(context -> mvc(context).perform(get("/slow")));

    assertThat(output).contains("WARN", "status=503").doesNotContain(STACK_FRAME);
  }

  @Test
  void should_answer_413_when_an_upload_is_too_large() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/too-large"))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.status").value(413)));
  }

  @Test
  void should_log_the_cause_of_a_client_error_for_the_server_only(CapturedOutput output) {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/paid-with-cause"))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString(SampleController.CAUSE)))));

    assertThat(output).contains(SampleController.CAUSE);
  }

  @Test
  void should_rethrow_a_security_exception_wrapped_by_a_proxy() {
    runner.run(
        context ->
            assertThatThrownBy(() -> mvc(context).perform(get("/wrapped-denied")))
                .hasRootCauseInstanceOf(AccessDeniedException.class));
  }

  @Test
  void should_strip_path_parameters_from_the_instance() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/orders/order-42;jsessionid=SESSION42"))
                .andExpect(jsonPath("$.instance").value("/orders/order-42")));
  }
}
