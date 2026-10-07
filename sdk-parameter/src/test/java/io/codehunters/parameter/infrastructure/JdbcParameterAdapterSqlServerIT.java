package io.codehunters.parameter.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.parameter.domain.model.Parameter;
import io.codehunters.parameter.domain.model.ParameterModule;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Regression test for the {@code DATETIMEOFFSET} mapping bug. Against a real SQL Server the driver
 * returns those columns as {@code microsoft.sql.DateTimeOffset}; the previous row mapper failed to
 * parse them. Uses azure-sql-edge for ARM compatibility, pinned to its final release.
 */
@Testcontainers
class JdbcParameterAdapterSqlServerIT {

  @Container
  static final MSSQLServerContainer SQL =
      new MSSQLServerContainer(
              DockerImageName.parse("mcr.microsoft.com/azure-sql-edge:1.0.7")
                  .asCompatibleSubstituteFor("mcr.microsoft.com/mssql/server"))
          .acceptLicense();

  private static JdbcParameterAdapter adapter;

  @BeforeAll
  static void setUp() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(SQL.getJdbcUrl(), SQL.getUsername(), SQL.getPassword());
    JdbcClient jdbcClient = JdbcClient.create(dataSource);
    jdbcClient
        .sql(
            "CREATE TABLE parameters ("
                + "id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),"
                + "module NVARCHAR(64) NOT NULL,"
                + "submodule NVARCHAR(64) NOT NULL DEFAULT '',"
                + "name NVARCHAR(128) NOT NULL,"
                + "value NVARCHAR(MAX) NOT NULL,"
                + "type NVARCHAR(16) NOT NULL,"
                + "enabled BIT NOT NULL DEFAULT 1,"
                + "description NVARCHAR(500) NULL,"
                + "created_by UNIQUEIDENTIFIER NOT NULL,"
                + "created_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),"
                + "updated_by UNIQUEIDENTIFIER NULL,"
                + "updated_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),"
                + "deleted_at DATETIMEOFFSET NULL)")
        .update();
    jdbcClient
        .sql(
            "INSERT INTO parameters (module, submodule, name, value, type, enabled, created_by)"
                + " VALUES ('RAFFLES', 'blockchain', 'URL_TRANSACTION_SCAN',"
                + " 'https://amoy.polygonscan.com/tx/', 'STRING', 1,"
                + " '00000000-0000-0000-0000-000000000000')")
        .update();
    adapter = new JdbcParameterAdapter(jdbcClient);
  }

  @Test
  void lookup_maps_datetimeoffset_columns_without_failing() {
    Optional<Parameter> result =
        adapter.lookup(ParameterModule.RAFFLES, "blockchain", "URL_TRANSACTION_SCAN");

    assertThat(result).isPresent();
    assertThat(result.get().value()).isEqualTo("https://amoy.polygonscan.com/tx/");
    assertThat(result.get().createdAt()).isNotNull();
    assertThat(result.get().updatedAt()).isNotNull();
  }
}
