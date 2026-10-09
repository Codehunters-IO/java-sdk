package io.codehunters.web.errors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiExceptionTest {

  private static final String ORDER_ID = "order-42";

  enum SampleCode implements ErrorCode {
    ORDER_NOT_FOUND;

    @Override
    public String code() {
      return name();
    }

    @Override
    public int status() {
      return 404;
    }
  }

  record CodeOnly(String code) implements ErrorCode {
    @Override
    public int status() {
      return 400;
    }
  }

  @Test
  void should_reject_an_error_code_without_code() {
    assertThatThrownBy(() -> new ApiException(new CodeOnly(null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_an_error_code_with_a_blank_code() {
    assertThatThrownBy(() -> new ApiException(new CodeOnly(" ")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_refuse_serialization_because_the_code_and_arguments_cannot_travel() {
    var exception = new ApiException(SampleCode.ORDER_NOT_FOUND, List.of(ORDER_ID));

    assertThatThrownBy(
            () -> new ObjectOutputStream(new ByteArrayOutputStream()).writeObject(exception))
        .isInstanceOf(NotSerializableException.class);
  }

  @Test
  void should_expose_the_error_code_and_arguments() {
    var exception = new ApiException(SampleCode.ORDER_NOT_FOUND, List.of(ORDER_ID));

    assertThat(exception.errorCode()).isEqualTo(SampleCode.ORDER_NOT_FOUND);
    assertThat(exception.arguments()).containsExactly(ORDER_ID);
  }

  @Test
  void should_use_only_the_code_as_message_when_arguments_carry_values() {
    var exception = new ApiException(SampleCode.ORDER_NOT_FOUND, List.of(ORDER_ID));

    assertThat(exception.getMessage()).isEqualTo("ORDER_NOT_FOUND");
  }

  @Test
  void should_have_no_arguments_when_created_with_the_code_only() {
    assertThat(new ApiException(SampleCode.ORDER_NOT_FOUND).arguments()).isEmpty();
  }

  @Test
  void should_keep_the_cause_when_given_one() {
    var cause = new IllegalStateException("boom");

    var exception = new ApiException(SampleCode.ORDER_NOT_FOUND, List.of(), cause);

    assertThat(exception.getCause()).isSameAs(cause);
  }

  @Test
  void should_copy_the_arguments_when_the_caller_changes_its_list() {
    var arguments = new ArrayList<Object>(List.of(ORDER_ID));
    var exception = new ApiException(SampleCode.ORDER_NOT_FOUND, arguments);

    arguments.clear();

    assertThat(exception.arguments()).containsExactly(ORDER_ID);
  }

  @Test
  void should_accept_null_arguments_when_a_message_placeholder_is_optional() {
    var exception = new ApiException(SampleCode.ORDER_NOT_FOUND, Arrays.asList(ORDER_ID, null));

    assertThat(exception.arguments()).containsExactly(ORDER_ID, null);
  }

  @Test
  void should_return_unmodifiable_arguments() {
    var exception = new ApiException(SampleCode.ORDER_NOT_FOUND, List.of(ORDER_ID));

    assertThatThrownBy(() -> exception.arguments().add("other"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void should_reject_a_missing_error_code() {
    assertThatThrownBy(() -> new ApiException(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("errorCode must not be null");
  }

  @Test
  void should_reject_missing_arguments() {
    assertThatThrownBy(() -> new ApiException(SampleCode.ORDER_NOT_FOUND, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("arguments must not be null");
  }

  @Test
  void should_derive_the_message_key_from_the_code() {
    assertThat(SampleCode.ORDER_NOT_FOUND.messageKey()).isEqualTo("error.ORDER_NOT_FOUND");
  }
}
