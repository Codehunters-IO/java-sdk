package io.codehunters.messaging.infrastructure.entrypoint.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxPurgeSettingsTest {

  private static final Clock CLOCK = Clock.systemUTC();
  private static final Duration RETENTION = Duration.ofDays(7);

  @Test
  void should_reject_settings_when_retention_is_zero() {
    assertThatThrownBy(() -> new OutboxPurgeSettings(Duration.ZERO, 500, CLOCK))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_settings_when_retention_is_null() {
    assertThatThrownBy(() -> new OutboxPurgeSettings(null, 500, CLOCK))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_settings_when_batch_size_is_zero() {
    assertThatThrownBy(() -> new OutboxPurgeSettings(RETENTION, 0, CLOCK))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_settings_when_clock_is_null() {
    assertThatThrownBy(() -> new OutboxPurgeSettings(RETENTION, 500, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_keep_values_when_settings_are_valid() {
    var settings = new OutboxPurgeSettings(RETENTION, 500, CLOCK);

    assertThat(settings.retention()).isEqualTo(RETENTION);
    assertThat(settings.batchSize()).isEqualTo(500);
    assertThat(settings.clock()).isSameAs(CLOCK);
  }
}
