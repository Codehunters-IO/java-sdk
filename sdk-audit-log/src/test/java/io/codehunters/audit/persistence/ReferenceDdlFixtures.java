package io.codehunters.audit.persistence;

import io.codehunters.audit.AuditEvent;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Shared fixtures for the integration tests that load the shipped reference DDL. */
final class ReferenceDdlFixtures {

  static final String POSTGRES_DDL = "db/migration/audit/postgresql/audit_event_create.sql";
  static final String SQLSERVER_DDL = "db/migration/audit/sqlserver/audit_event_create.sql";
  static final UUID CORRELATION_ID = UUID.fromString("4bf92f35-77b3-4da6-a3ce-929d0e0e4736");
  static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-10-06T12:00:00Z");

  private ReferenceDdlFixtures() {}

  static void load(DataSource dataSource, String ddl) {
    new ResourceDatabasePopulator(new ClassPathResource(ddl)).execute(dataSource);
  }

  static AuditEvent fullEvent() {
    return new AuditEvent(
        "ORDER",
        "ORD-001",
        "CREATE",
        "user-1",
        "ADMIN",
        "10.0.0.1",
        Map.of("amount", 99.99),
        "SUCCESS",
        CORRELATION_ID,
        "order-service",
        CREATED_AT,
        "/api/orders",
        "res-001",
        "POST",
        201,
        "Mozilla/5.0",
        Map.of("status", "ok"),
        true,
        null,
        150,
        Map.of("env", "prod"));
  }
}
