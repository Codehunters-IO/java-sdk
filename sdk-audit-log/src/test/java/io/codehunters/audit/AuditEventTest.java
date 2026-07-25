package io.codehunters.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditEventTest {

  @Test
  @DisplayName("should_default_extended_fields_to_null_when_legacy_constructor_used")
  void legacy_constructor_defaults_extended_to_null() {
    UUID correlation = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now();

    AuditEvent event =
        new AuditEvent(
            "Order",
            "42",
            "CREATE",
            "user-1",
            "USER",
            "10.0.0.1",
            Map.of("k", "v"),
            "SUCCESS",
            correlation,
            "vitxo-ms-test",
            now);

    assertThat(event.entityType()).isEqualTo("Order");
    assertThat(event.entityId()).isEqualTo("42");
    assertThat(event.action()).isEqualTo("CREATE");
    assertThat(event.payload()).containsEntry("k", "v");
    assertThat(event.correlationId()).isEqualTo(correlation);
    assertThat(event.createdAt()).isEqualTo(now);
    assertThat(event.resource()).isNull();
    assertThat(event.httpStatus()).isNull();
    assertThat(event.success()).isNull();
    assertThat(event.metadata()).isNull();
  }

  @Test
  @DisplayName("should_round_trip_all_fields_when_full_constructor_used")
  void full_constructor_preserves_fields() {
    AuditEvent event =
        new AuditEvent(
            "Order",
            "1",
            "UPDATE",
            "user",
            "USER",
            "ip",
            Map.of(),
            "SUCCESS",
            UUID.randomUUID(),
            "svc",
            OffsetDateTime.now(),
            "/orders",
            "1",
            "POST",
            201,
            "ua",
            Map.of("r", 1),
            Boolean.TRUE,
            null,
            150,
            Map.of("m", 2));

    assertThat(event.resource()).isEqualTo("/orders");
    assertThat(event.httpStatus()).isEqualTo(201);
    assertThat(event.userAgent()).isEqualTo("ua");
    assertThat(event.responseSummary()).containsEntry("r", 1);
    assertThat(event.success()).isTrue();
    assertThat(event.durationMs()).isEqualTo(150);
    assertThat(event.metadata()).containsEntry("m", 2);
  }
}
