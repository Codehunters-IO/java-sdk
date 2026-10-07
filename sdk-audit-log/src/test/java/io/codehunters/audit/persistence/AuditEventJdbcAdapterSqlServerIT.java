package io.codehunters.audit.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes an event through the adapter into a table created from the SQL Server reference DDL. Uses
 * azure-sql-edge for ARM compatibility, pinned to its final release.
 */
@Testcontainers
class AuditEventJdbcAdapterSqlServerIT {

  @Container
  static final MSSQLServerContainer DB =
      new MSSQLServerContainer(
              DockerImageName.parse("mcr.microsoft.com/azure-sql-edge:1.0.7")
                  .asCompatibleSubstituteFor("mcr.microsoft.com/mssql/server"))
          .acceptLicense();

  private static DataSource dataSource;

  @BeforeAll
  static void createTable() {
    dataSource = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    ReferenceDdlFixtures.load(dataSource, ReferenceDdlFixtures.SQLSERVER_DDL);
  }

  @Test
  void should_persist_every_column_into_the_reference_table() {
    new AuditEventJdbcAdapter(dataSource, JsonMapper.builder().build(), true)
        .publish(ReferenceDdlFixtures.fullEvent());

    Map<String, Object> row =
        JdbcClient.create(dataSource)
            .sql(
                "SELECT event_type, http_status, success, duration_ms, request_body,"
                    + " CAST(correlation_id AS NVARCHAR(36)) AS correlation_id,"
                    + " CONVERT(NVARCHAR(40), event_created_at, 127) AS created_at"
                    + " FROM audit_event WHERE entity_id = 'ORD-001'")
            .query()
            .singleRow();
    assertThat(row)
        .containsEntry("event_type", "ORDER")
        .containsEntry("http_status", 201)
        .containsEntry("success", true)
        .containsEntry("duration_ms", 150)
        .containsEntry("request_body", "{\"amount\":99.99}")
        .containsEntry("correlation_id", "4BF92F35-77B3-4DA6-A3CE-929D0E0E4736")
        .containsEntry("created_at", "2026-10-06T12:00:00Z");
  }
}
