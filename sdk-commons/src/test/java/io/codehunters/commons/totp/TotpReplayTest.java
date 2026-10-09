package io.codehunters.commons.totp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class TotpReplayTest {

  private static final TotpSecret SECRET =
      TotpSecret.fromHex("3132333435363738393031323334353637383930");
  private static final Instant AT = Instant.ofEpochSecond(95);
  private static final long CURRENT_STEP = 3;

  private final TotpGenerator generator =
      new TotpGenerator(TotpConfig.defaultConfig(), Clock.fixed(AT, ZoneOffset.UTC));

  @Test
  void should_return_current_step_when_code_is_current() {
    var code = generator.generate(SECRET, AT);

    assertThat(generator.verifyStep(SECRET, code, 1, AT)).isEqualTo(OptionalLong.of(CURRENT_STEP));
  }

  @Test
  void should_return_previous_step_when_code_is_one_step_old() {
    var code = generator.generate(SECRET, AT.minusSeconds(30));

    assertThat(generator.verifyStep(SECRET, code, 1, AT))
        .isEqualTo(OptionalLong.of(CURRENT_STEP - 1));
  }

  @Test
  void should_return_next_step_when_code_is_one_step_ahead() {
    var code = generator.generate(SECRET, AT.plusSeconds(30));

    assertThat(generator.verifyStep(SECRET, code, 1, AT))
        .isEqualTo(OptionalLong.of(CURRENT_STEP + 1));
  }

  @Test
  void should_return_no_step_when_code_is_wrong() {
    assertThat(generator.verifyStep(SECRET, "000000", 1, AT)).isEmpty();
  }

  @Test
  void should_return_no_step_when_code_is_null() {
    assertThat(generator.verifyStep(SECRET, null, 1, AT)).isEmpty();
  }

  @Test
  void should_return_no_step_when_code_is_outside_variance() {
    var code = generator.generate(SECRET, AT.minusSeconds(60));

    assertThat(generator.verifyStep(SECRET, code, 1, AT)).isEmpty();
  }

  @Test
  void should_reject_variance_above_ten_when_verifying_step() {
    assertThatThrownBy(() -> generator.verifyStep(SECRET, "000000", 11, AT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("variance");
  }

  @Test
  void should_read_clock_when_verifying_step_without_instant() {
    var code = generator.generate(SECRET, AT);

    assertThat(generator.verifyStep(SECRET, code, 0)).isEqualTo(OptionalLong.of(CURRENT_STEP));
  }

  @Test
  void should_accept_code_when_no_step_was_accepted_before() {
    var code = generator.generate(SECRET, AT);

    assertThat(generator.verifyAfter(SECRET, code, 1, TotpGenerator.NO_ACCEPTED_STEP))
        .isEqualTo(OptionalLong.of(CURRENT_STEP));
  }

  @Test
  void should_accept_code_when_its_step_is_after_last_accepted() {
    var code = generator.generate(SECRET, AT);

    assertThat(generator.verifyAfter(SECRET, code, 1, CURRENT_STEP - 1))
        .isEqualTo(OptionalLong.of(CURRENT_STEP));
  }

  @Test
  void should_reject_code_when_its_step_was_already_accepted() {
    var code = generator.generate(SECRET, AT);

    assertThat(generator.verifyAfter(SECRET, code, 1, CURRENT_STEP)).isEmpty();
  }

  @Test
  void should_reject_older_code_when_a_later_step_was_accepted() {
    var code = generator.generate(SECRET, AT.minusSeconds(30));

    assertThat(generator.verifyAfter(SECRET, code, 1, CURRENT_STEP)).isEmpty();
  }

  @Test
  void should_reject_wrong_code_when_verifying_after_last_step() {
    assertThat(generator.verifyAfter(SECRET, "000000", 1, CURRENT_STEP - 1)).isEmpty();
  }

  @Test
  void should_reject_null_code_when_verifying_after_last_step() {
    assertThat(generator.verifyAfter(SECRET, null, 1, CURRENT_STEP - 1)).isEmpty();
  }

  @Test
  void should_reject_negative_variance_when_verifying_after_last_step() {
    assertThatThrownBy(() -> generator.verifyAfter(SECRET, "000000", -1, CURRENT_STEP))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("variance");
  }
}
