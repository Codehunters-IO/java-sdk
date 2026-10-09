package io.codehunters.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MessagePublishExceptionTest {

  private static final String PII_SENTINEL = "PII-SENTINEL-4242 holder Juan";

  @Test
  void should_carry_fixed_message_without_cause_text_when_wrapping_cause() {
    RuntimeException cause = new RuntimeException(PII_SENTINEL);

    MessagePublishException exception = new MessagePublishException(cause);

    assertThat(exception.getMessage()).isEqualTo("Broker publish failed");
    assertThat(exception.toString()).doesNotContain("PII-SENTINEL");
    assertThat(exception.getCause()).isSameAs(cause);
  }
}
