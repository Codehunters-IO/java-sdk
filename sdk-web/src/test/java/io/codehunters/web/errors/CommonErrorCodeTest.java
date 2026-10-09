package io.codehunters.web.errors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommonErrorCodeTest {

  @Test
  void should_map_validation_failures_to_bad_request() {
    assertThat(CommonErrorCode.VALIDATION_FAILED.status()).isEqualTo(400);
    assertThat(CommonErrorCode.VALIDATION_FAILED.code()).isEqualTo("VALIDATION_FAILED");
  }

  @Test
  void should_map_unexpected_failures_to_internal_server_error() {
    assertThat(CommonErrorCode.INTERNAL_ERROR.status()).isEqualTo(500);
    assertThat(CommonErrorCode.INTERNAL_ERROR.messageKey()).isEqualTo("error.INTERNAL_ERROR");
  }
}
