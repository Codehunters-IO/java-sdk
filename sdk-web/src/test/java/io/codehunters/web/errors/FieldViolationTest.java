package io.codehunters.web.errors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FieldViolationTest {

  @Test
  void should_hold_field_code_and_message() {
    var violation = new FieldViolation("name", "NotBlank", "must not be blank");

    assertThat(violation.field()).isEqualTo("name");
    assertThat(violation.code()).isEqualTo("NotBlank");
    assertThat(violation.message()).isEqualTo("must not be blank");
  }

  @Test
  void should_reject_a_missing_field() {
    assertThatThrownBy(() -> new FieldViolation(null, "NotBlank", "must not be blank"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("field must not be null");
  }

  @Test
  void should_reject_a_missing_code() {
    assertThatThrownBy(() -> new FieldViolation("name", null, "must not be blank"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("code must not be null");
  }

  @Test
  void should_reject_a_missing_message() {
    assertThatThrownBy(() -> new FieldViolation("name", "NotBlank", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("message must not be null");
  }
}
