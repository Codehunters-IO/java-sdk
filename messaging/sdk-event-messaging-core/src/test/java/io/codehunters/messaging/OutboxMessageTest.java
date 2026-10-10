package io.codehunters.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxMessageTest {

  private static final Instant AT = Instant.parse("2026-10-09T10:00:00Z");
  private static final String MARKER = "instance:claim";

  @Test
  void should_build_pending_message_without_attempts_when_status_is_not_set() {
    OutboxMessage<String> message = OutboxMessage.<String>builder().topic("t").build();

    assertThat(message.status()).isEqualTo(OutboxStatus.PENDING);
    assertThat(message.attempts()).isZero();
  }

  @Test
  void should_default_status_to_pending_when_constructed_with_null_status() {
    OutboxMessage<String> message = OutboxMessage.<String>builder().status(null).build();

    assertThat(message.status()).isEqualTo(OutboxStatus.PENDING);
  }

  @Test
  void should_copy_every_component_when_rebuilt() {
    OutboxMessage<String> message = full();

    assertThat(message.toBuilder().build()).isEqualTo(message);
  }

  @Test
  void should_leave_original_untouched_when_published() {
    OutboxMessage<String> claimed = claimed();

    OutboxMessage<String> published = claimed.published(AT);

    assertThat(claimed.status()).isEqualTo(OutboxStatus.IN_FLIGHT);
    assertThat(published.status()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(published.publishedAt()).isEqualTo(AT);
    assertThat(published.error()).isNull();
    assertThat(published.nextAttemptAt()).isNull();
    assertThat(published.claimedBy()).isEqualTo(MARKER);
  }

  @Test
  void should_charge_one_attempt_and_record_error_when_attempt_failed() {
    OutboxMessage<String> failed = claimed().attemptFailed("java.io.IOException");

    assertThat(failed.attempts()).isEqualTo(2);
    assertThat(failed.error()).isEqualTo("java.io.IOException");
    assertThat(failed.status()).isEqualTo(OutboxStatus.IN_FLIGHT);
  }

  @Test
  void should_be_pending_until_next_attempt_when_retried() {
    OutboxMessage<String> retried = claimed().retryAt(AT);

    assertThat(retried.status()).isEqualTo(OutboxStatus.PENDING);
    assertThat(retried.nextAttemptAt()).isEqualTo(AT);
  }

  @Test
  void should_be_failed_without_next_attempt_when_given_up() {
    OutboxMessage<String> failed = claimed().retryAt(AT).failed("reason");

    assertThat(failed.status()).isEqualTo(OutboxStatus.FAILED);
    assertThat(failed.error()).isEqualTo("reason");
    assertThat(failed.nextAttemptAt()).isNull();
    assertThat(failed.attempts()).isEqualTo(1);
  }

  @Test
  void should_be_pending_now_without_claim_time_and_keep_marker_when_released() {
    OutboxMessage<String> released = claimed().released(AT);

    assertThat(released.status()).isEqualTo(OutboxStatus.PENDING);
    assertThat(released.claimedAt()).isNull();
    assertThat(released.claimedBy()).isEqualTo(MARKER);
    assertThat(released.nextAttemptAt()).isEqualTo(AT);
    assertThat(released.attempts()).isEqualTo(1);
  }

  @Test
  void should_record_send_mark_when_send_started() {
    assertThat(claimed().sendStarted(AT).sendStartedAt()).isEqualTo(AT);
  }

  private static OutboxMessage<String> claimed() {
    return OutboxMessage.<String>builder()
        .id(UUID.randomUUID())
        .topic("events")
        .message("{}")
        .status(OutboxStatus.IN_FLIGHT)
        .attempts(1)
        .claimedAt(AT)
        .claimedBy(MARKER)
        .build();
  }

  private static OutboxMessage<String> full() {
    return OutboxMessage.<String>builder()
        .id(UUID.randomUUID())
        .seq(7L)
        .origin("payment-ms")
        .routingKey("payment.done")
        .topic("payments")
        .key("pay-1")
        .message("{}")
        .status(OutboxStatus.IN_FLIGHT)
        .attempts(2)
        .error("timeout")
        .occurredAt(AT)
        .nextAttemptAt(AT)
        .publishedAt(AT)
        .claimedAt(AT)
        .claimedBy(MARKER)
        .sendStartedAt(AT)
        .traceparent("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
        .build();
  }
}
