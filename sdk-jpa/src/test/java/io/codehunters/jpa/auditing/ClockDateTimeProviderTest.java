package io.codehunters.jpa.auditing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClockDateTimeProviderTest {

  @Test
  void should_read_now_from_the_clock_in_microseconds_as_databases_store_it() {
    var clock = Clock.fixed(Instant.parse("2026-10-07T10:15:30.123456789Z"), ZoneOffset.UTC);

    assertThat(new ClockDateTimeProvider(clock).getNow())
        .contains(Instant.parse("2026-10-07T10:15:30.123456Z"));
  }

  @Test
  void should_reject_a_missing_clock() {
    assertThatThrownBy(() -> new ClockDateTimeProvider(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
