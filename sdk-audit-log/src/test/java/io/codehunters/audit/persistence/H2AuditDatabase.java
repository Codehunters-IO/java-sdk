package io.codehunters.audit.persistence;

import io.codehunters.audit.AuditEvent;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * An in-memory database with the test audit table, plus the events the tests write into it.
 *
 * <p>Unit tests stay on H2 because the adapter's INSERT is portable SQL and H2 keeps them fast.
 * {@code AuditEventJdbcAdapterPostgresIT} checks that this schema has the same columns as the
 * PostgreSQL reference DDL, so the two cannot drift.
 */
final class H2AuditDatabase {

  static final AuditEvent SUCCESS = event("SUCCESS", true);
  static final AuditEvent ERROR = event("ERROR", false);

  private H2AuditDatabase() {}

  static DataSource create() {
    var database = new DriverManagerDataSource();
    database.setUrl(
        "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
    new ResourceDatabasePopulator(new ClassPathResource("db/test-schema.sql")).execute(database);
    return database;
  }

  static int countRows(DataSource dataSource) {
    return JdbcClient.create(dataSource)
        .sql("SELECT COUNT(*) FROM audit_event")
        .query(Integer.class)
        .single();
  }

  private static AuditEvent event(String status, boolean success) {
    return new AuditEvent(
        "ORDER",
        "ORD-1",
        "CREATE",
        "user-1",
        "USER",
        null,
        Map.of(),
        status,
        null,
        "order-service",
        OffsetDateTime.parse("2026-10-06T12:00:00Z"),
        null,
        null,
        null,
        null,
        null,
        null,
        success,
        null,
        5,
        null);
  }
}
