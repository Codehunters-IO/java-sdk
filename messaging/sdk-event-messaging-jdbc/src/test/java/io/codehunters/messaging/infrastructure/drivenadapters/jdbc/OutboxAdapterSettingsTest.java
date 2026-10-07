package io.codehunters.messaging.infrastructure.drivenadapters.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class OutboxAdapterSettingsTest {

  private static final OutboxDialect DIALECT = new GenericOutboxDialect();
  private static final Clock CLOCK = Clock.systemUTC();

  @Test
  void should_reject_blank_origin() {
    assertThatThrownBy(() -> new OutboxAdapterSettings("  ", DIALECT, CLOCK))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_null_origin() {
    assertThatThrownBy(() -> new OutboxAdapterSettings(null, DIALECT, CLOCK))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void should_reject_null_dialect() {
    assertThatThrownBy(() -> new OutboxAdapterSettings("svc", null, CLOCK))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void should_reject_null_clock() {
    assertThatThrownBy(() -> new OutboxAdapterSettings("svc", DIALECT, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void should_resolve_dialect_and_utc_clock_when_defaults_used() {
    var dataSource =
        new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .generateUniqueName(true)
            .build();

    var settings = OutboxAdapterSettings.defaults(dataSource, "svc");

    assertThat(settings.origin()).isEqualTo("svc");
    assertThat(settings.dialect()).isInstanceOf(GenericOutboxDialect.class);
    assertThat(settings.clock().getZone()).isEqualTo(ZoneOffset.UTC);
  }
}
