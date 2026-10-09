package io.codehunters.messaging.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OutboxAdapterSettingsTest {

  private static final Clock CLOCK = Clock.systemUTC();

  @Test
  void should_reject_blank_origin() {
    assertThatThrownBy(() -> new OutboxAdapterSettings("  ", CLOCK))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_null_origin() {
    assertThatThrownBy(() -> new OutboxAdapterSettings(null, CLOCK))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void should_reject_null_clock() {
    assertThatThrownBy(() -> new OutboxAdapterSettings("svc", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void should_use_utc_clock_when_defaults_used() {
    var settings = OutboxAdapterSettings.defaults("svc");

    assertThat(settings.origin()).isEqualTo("svc");
    assertThat(settings.clock().getZone()).isEqualTo(ZoneOffset.UTC);
  }
}
