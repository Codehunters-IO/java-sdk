package io.codehunters.weberrors.web;

import static io.codehunters.weberrors.web.WebMvcFixture.mvc;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;

class ValidationHandlingTest {

  private static final String REJECTED_QUANTITY = "-73519";
  private static final String INVALID_ORDER =
      "{\"name\":\"\",\"quantity\":" + REJECTED_QUANTITY + "}";
  private static final String REJECTED_QUERY = "toolongvalue";

  private final WebApplicationContextRunner runner = WebMvcFixture.runner();

  @Test
  void should_list_each_field_violation_when_the_request_body_is_invalid() {
    runner.run(
        context ->
            mvc(context)
                .perform(
                    post("/orders").contentType(MediaType.APPLICATION_JSON).content(INVALID_ORDER))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("NotBlank"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"))
                .andExpect(jsonPath("$.errors[1].field").value("quantity"))
                .andExpect(jsonPath("$.errors[1].code").value("Positive")));
  }

  @Test
  void should_never_echo_the_rejected_value_when_the_request_body_is_invalid() {
    runner.run(
        context ->
            mvc(context)
                .perform(
                    post("/orders").contentType(MediaType.APPLICATION_JSON).content(INVALID_ORDER))
                .andExpect(content().string(not(containsString(REJECTED_QUANTITY)))));
  }

  @Test
  void should_list_parameter_violations_when_method_validation_fails() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/search").param("q", REJECTED_QUERY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("q"))
                .andExpect(jsonPath("$.errors[0].code").value("Size"))
                .andExpect(content().string(not(containsString(REJECTED_QUERY)))));
  }

  @Test
  void should_list_violations_when_a_service_throws_a_constraint_violation_exception() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/service"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].code").value("NotBlank")));
  }

  @Test
  void should_use_the_reason_phrase_as_detail_when_no_validation_message_is_defined() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/service"))
                .andExpect(jsonPath("$.detail").value("Bad Request")));
  }
}
