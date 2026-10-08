package io.codehunters.weberrors.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

class StatusCodesTest {

  private static final HttpStatusCode UNNAMED = HttpStatusCode.valueOf(499);

  @Test
  void should_name_an_unnamed_status_by_its_value() {
    assertThat(StatusCodes.code(UNNAMED)).isEqualTo("HTTP_499");
  }

  @Test
  void should_use_a_neutral_reason_phrase_when_the_status_has_none() {
    assertThat(StatusCodes.reasonPhrase(UNNAMED)).isEqualTo("Error");
  }

  @Test
  void should_keep_an_unnamed_status_when_it_is_an_error() {
    assertThat(StatusCodes.errorStatus(499)).isEqualTo(UNNAMED);
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 204, 302, 399, 600})
  void should_answer_500_when_the_declared_status_is_not_an_error(int status) {
    assertThat(StatusCodes.errorStatus(status)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
