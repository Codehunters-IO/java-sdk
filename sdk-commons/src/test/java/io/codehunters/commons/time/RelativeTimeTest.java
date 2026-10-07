package io.codehunters.commons.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RelativeTimeTest {

  private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

  @ParameterizedTest
  @CsvSource({
    "0, just now",
    "-59, just now",
    "-60, 1 minute ago",
    "-119, 1 minute ago",
    "-3599, 59 minutes ago",
    "-3600, 1 hour ago",
    "-86399, 23 hours ago",
    "-86400, 1 day ago",
    "-604799, 6 days ago",
    "-604800, 1 week ago",
    "-2591999, 4 weeks ago",
    "-2592000, 1 month ago",
    "-31535999, 12 months ago",
    "-31536000, 1 year ago",
    "-94608000, 3 years ago",
    "7200, in 2 hours",
    "59, just now"
  })
  void should_describe_the_offset_in_english(long offsetSeconds, String expected) {
    var relative = RelativeTime.between(NOW.plusSeconds(offsetSeconds), NOW);

    assertThat(relative.toEnglish()).isEqualTo(expected);
  }

  @Test
  void should_expose_amount_unit_and_direction_for_localized_rendering() {
    var relative = RelativeTime.between(NOW.minusSeconds(3 * 86400), NOW);

    assertThat(relative)
        .isEqualTo(new RelativeTime(3, RelativeTime.Unit.DAYS, RelativeTime.Direction.PAST));
  }

  @Test
  void should_mark_future_instants() {
    assertThat(RelativeTime.between(NOW.plusSeconds(600), NOW).direction())
        .isEqualTo(RelativeTime.Direction.FUTURE);
  }

  @Test
  void should_reject_null_instants() {
    assertThatIllegalArgumentException().isThrownBy(() -> RelativeTime.between(null, NOW));
  }

  @Test
  void should_reject_negative_amount() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new RelativeTime(-1, RelativeTime.Unit.DAYS, RelativeTime.Direction.PAST));
  }

  @Test
  void should_reject_missing_unit_or_direction() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RelativeTime(1, null, RelativeTime.Direction.PAST));
  }
}
