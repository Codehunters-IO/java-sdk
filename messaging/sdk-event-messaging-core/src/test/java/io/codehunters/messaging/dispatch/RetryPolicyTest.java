package io.codehunters.messaging.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.messaging.ClaimRequest;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

  private static final Duration BASE = Duration.ofMillis(100);
  private static final Duration MAX = Duration.ofMinutes(1);
  private static final RetryPolicy POLICY = new RetryPolicy(3, BASE, MAX);

  @Test
  void should_wait_base_delay_when_first_attempt_failed() {
    assertThat(POLICY.backoff(1)).isEqualTo(BASE);
  }

  @Test
  void should_double_backoff_with_each_attempt_when_not_capped() {
    assertThat(POLICY.backoff(2)).isEqualTo(BASE.multipliedBy(2));
    assertThat(POLICY.backoff(3)).isEqualTo(BASE.multipliedBy(4));
  }

  @Test
  void should_wait_base_delay_when_no_attempt_was_charged() {
    assertThat(POLICY.backoff(0)).isEqualTo(BASE);
  }

  @Test
  void should_cap_backoff_at_max_delay_when_attempts_are_large() {
    assertThat(POLICY.backoff(30)).isEqualTo(MAX);
    assertThat(POLICY.backoff(100)).isEqualTo(MAX);
  }

  @Test
  void should_cap_backoff_at_max_delay_when_doubling_overflows() {
    RetryPolicy huge = new RetryPolicy(3, Duration.ofSeconds(Long.MAX_VALUE / 2), MAX);

    assertThat(huge.backoff(31)).isEqualTo(MAX);
  }

  @Test
  void should_be_exhausted_when_attempts_reach_max() {
    assertThat(POLICY.isExhausted(3)).isTrue();
    assertThat(POLICY.isExhausted(4)).isTrue();
  }

  @Test
  void should_not_be_exhausted_when_attempts_below_max() {
    assertThat(POLICY.isExhausted(2)).isFalse();
  }

  @Test
  void should_reject_policy_when_max_attempts_below_one() {
    assertThatThrownBy(() -> new RetryPolicy(0, BASE, MAX))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxAttempts");
  }

  @Test
  void should_reject_policy_when_base_delay_is_zero() {
    assertThatThrownBy(() -> new RetryPolicy(1, Duration.ZERO, MAX))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("baseDelay");
  }

  @Test
  void should_reject_policy_when_max_delay_is_negative() {
    assertThatThrownBy(() -> new RetryPolicy(1, BASE, Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxDelay");
  }

  @Test
  void should_reject_policy_when_base_delay_is_null() {
    assertThatThrownBy(() -> new RetryPolicy(1, null, MAX))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void should_reject_settings_when_a_component_is_null() {
    ClaimRequest claim = new ClaimRequest("o", "c", 1, Duration.ofSeconds(1), 1);

    assertThatThrownBy(() -> new DispatchSettings(claim, null, Clock.systemUTC()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("retry");
  }
}
