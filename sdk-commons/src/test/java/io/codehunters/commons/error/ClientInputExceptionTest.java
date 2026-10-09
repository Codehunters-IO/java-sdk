package io.codehunters.commons.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ClientInputExceptionTest {

  private static final String CODE = "INVALID_SORT";
  private static final String MESSAGE = "sort property is not allowed";

  @Test
  void should_expose_the_code_and_the_message_when_created() {
    var exception = new ClientInputException(CODE, MESSAGE);

    assertThat(exception.code()).isEqualTo(CODE);
    assertThat(exception.getMessage()).isEqualTo(MESSAGE);
    assertThat(exception.getCause()).isNull();
  }

  @Test
  void should_keep_the_cause_when_created_with_one() {
    var cause = new IllegalStateException("parser failed");

    var exception = new ClientInputException(CODE, MESSAGE, cause);

    assertThat(exception.getCause()).isSameAs(cause);
  }

  @Test
  void should_stay_an_illegal_argument_exception_so_existing_catch_blocks_keep_working() {
    assertThat(new ClientInputException(CODE, MESSAGE)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"A", "INVALID_SORT", "BARCODE_IMAGE_REJECTED", "E2", "X_9_Y"})
  void should_accept_an_upper_snake_case_code(String code) {
    assertThat(new ClientInputException(code, MESSAGE).code()).isEqualTo(code);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"invalid_sort", "Invalid", "_SORT", "9SORT", "INVALID-SORT", "A B", "É"})
  void should_reject_a_code_that_is_not_upper_snake_case(String code) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ClientInputException(code, MESSAGE))
        .withMessage("code must match [A-Z][A-Z0-9_]*");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void should_reject_a_blank_message(String message) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ClientInputException(CODE, message))
        .withMessage("message must not be blank");
  }

  @Test
  void should_keep_the_code_when_serialized() throws IOException, ClassNotFoundException {
    var restored = roundTrip(new ClientInputException(CODE, MESSAGE));

    assertThat(restored.code()).isEqualTo(CODE);
    assertThat(restored.getMessage()).isEqualTo(MESSAGE);
  }

  private static ClientInputException roundTrip(ClientInputException exception)
      throws IOException, ClassNotFoundException {
    var bytes = new ByteArrayOutputStream();
    try (var out = new ObjectOutputStream(bytes)) {
      out.writeObject(exception);
    }
    try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (ClientInputException) in.readObject();
    }
  }
}
