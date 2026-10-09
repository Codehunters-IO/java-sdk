package io.codehunters.commons.totp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class TotpExceptionTest {

  @Test
  void should_expose_message_and_cause() {
    var cause = new IllegalStateException("boom");
    var exception = new TotpException("failed", cause);

    assertEquals("failed", exception.getMessage());
    assertSame(cause, exception.getCause());
  }
}
