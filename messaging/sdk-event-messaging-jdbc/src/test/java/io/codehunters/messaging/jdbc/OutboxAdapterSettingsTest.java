package io.codehunters.messaging.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.messaging.OutboxDispatchTrigger;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OutboxAdapterSettingsTest {

  private static final Clock CLOCK = Clock.systemUTC();

  @Test
  void should_reject_blank_origin() {
    assertThatThrownBy(() -> new OutboxAdapterSettings("  ", CLOCK, OutboxDispatchTrigger.none()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_null_origin() {
    assertThatThrownBy(() -> new OutboxAdapterSettings(null, CLOCK, OutboxDispatchTrigger.none()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void should_reject_null_clock() {
    assertThatThrownBy(() -> new OutboxAdapterSettings("svc", null, OutboxDispatchTrigger.none()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void should_use_utc_clock_when_defaults_used() {
    var settings = OutboxAdapterSettings.defaults("svc");

    assertThat(settings.origin()).isEqualTo("svc");
    assertThat(settings.clock().getZone()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void should_not_trigger_dispatch_when_defaults_used() {
    var settings = OutboxAdapterSettings.defaults("svc");

    assertThat(settings.trigger()).isSameAs(OutboxDispatchTrigger.none());
  }

  @Test
  void should_reject_null_trigger() {
    assertThatThrownBy(() -> new OutboxAdapterSettings("svc", CLOCK, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("trigger");
  }
}
