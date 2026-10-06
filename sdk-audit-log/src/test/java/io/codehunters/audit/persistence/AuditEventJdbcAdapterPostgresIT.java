package io.codehunters.audit.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/** Writes an event through the adapter into a table created from the Postgres reference DDL. */
@Testcontainers
class AuditEventJdbcAdapterPostgresIT {

  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17-alpine");

  private static DataSource dataSource;

  @BeforeAll
  static void createTable() {
    dataSource = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    ReferenceDdlFixtures.load(dataSource, ReferenceDdlFixtures.POSTGRES_DDL);
  }

  @Test
  void should_persist_every_column_into_the_reference_table() {
    new AuditEventJdbcAdapter(dataSource, JsonMapper.builder().build(), true)
        .publish(ReferenceDdlFixtures.fullEvent());

    Map<String, Object> row =
        JdbcClient.create(dataSource)
            .sql("SELECT * FROM audit_event WHERE entity_id = 'ORD-001'")
            .query()
            .singleRow();
    assertThat(row)
        .containsEntry("event_type", "ORDER")
        .containsEntry("http_status", 201)
        .containsEntry("success", true)
        .containsEntry("duration_ms", 150)
        .containsEntry("correlation_id", ReferenceDdlFixtures.CORRELATION_ID)
        .containsEntry("request_body", "{\"amount\":99.99}");
    assertThat(row.get("id")).isInstanceOf(UUID.class);
    assertThat(
            JdbcClient.create(dataSource)
                .sql("SELECT event_created_at FROM audit_event WHERE entity_id = 'ORD-001'")
                .query(OffsetDateTime.class)
                .single())
        .isEqualTo(ReferenceDdlFixtures.CREATED_AT);
  }
}
