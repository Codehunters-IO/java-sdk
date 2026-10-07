package io.codehunters.commons.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CronExpressionsTest {

  @Test
  void should_build_quartz_expression_firing_once_at_the_given_time() {
    var at = LocalDateTime.of(2026, 10, 7, 9, 5, 3);

    assertThat(CronExpressions.quartzAt(at)).isEqualTo("3 5 9 7 10 ? 2026");
  }

  @Test
  void should_ignore_fractions_of_a_second() {
    var at = LocalDateTime.of(2026, 1, 31, 23, 59, 59, 999_000_000);

    assertThat(CronExpressions.quartzAt(at)).isEqualTo("59 59 23 31 1 ? 2026");
  }

  @Test
  void should_reject_null_time() {
    assertThatIllegalArgumentException().isThrownBy(() -> CronExpressions.quartzAt(null));
  }
}
