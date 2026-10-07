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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class ValidationHandlingTest {

  private static final String REJECTED_QUANTITY = "-73519";
  private static final String INVALID_ORDER =
      "{\"name\":\"\",\"quantity\":" + REJECTED_QUANTITY + "}";
  private static final String REJECTED_QUERY = "toolongvalue";
  private static final String REJECTED_CONVERSION = "abc-rejected";
  private static final String REJECTED_COUPON = "lowercase-coupon";
  private static final String COUPON = "{\"code\":\"" + REJECTED_COUPON + "\"}";

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
  void should_list_violations_with_the_parameter_path_when_a_validated_service_rejects_input() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/service"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("create.order.name"))
                .andExpect(jsonPath("$.errors[0].code").value("NotBlank")));
  }

  @Test
  void should_answer_500_when_a_constraint_violation_does_not_come_from_method_parameters() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/entity"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.errors").doesNotExist()));
  }

  @Test
  void should_answer_500_without_violations_when_the_return_value_fails_validation() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/codes"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.errors").doesNotExist()));
  }

  @Test
  void should_report_a_fixed_message_when_a_model_attribute_fails_to_convert() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/filter").param("quantity", REJECTED_CONVERSION))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"))
                .andExpect(jsonPath("$.errors[0].code").value("typeMismatch"))
                .andExpect(jsonPath("$.errors[0].message").value("Invalid value")));
  }

  @Test
  void should_never_echo_the_input_or_type_names_when_a_model_attribute_fails_to_convert() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/filter").param("quantity", REJECTED_CONVERSION))
                .andExpect(content().string(not(containsString(REJECTED_CONVERSION))))
                .andExpect(content().string(not(containsString("java.lang")))));
  }

  @Test
  void should_not_format_client_input_as_a_message_pattern() {
    runner.run(
        context ->
            mvc(context)
                .perform(get("/filter").param("quantity", "{0}"))
                .andExpect(jsonPath("$.errors[0].message").value("Invalid value"))
                .andExpect(content().string(not(containsString("{0}")))));
  }

  @Test
  void should_resolve_the_type_mismatch_message_from_the_bundle_when_one_is_defined() {
    runner.run(
        context ->
            mvc(context)
                .perform(
                    get("/filter")
                        .param("quantity", REJECTED_CONVERSION)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "es"))
                .andExpect(jsonPath("$.errors[0].message").value("Valor con formato incorrecto")));
  }

  @Test
  void should_hide_the_rejected_value_when_the_constraint_message_template_references_it() {
    runner.run(
        context ->
            mvc(context)
                .perform(post("/coupons").contentType(MediaType.APPLICATION_JSON).content(COUPON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Invalid value"))
                .andExpect(content().string(not(containsString(REJECTED_COUPON)))));
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
