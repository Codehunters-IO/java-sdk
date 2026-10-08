package io.codehunters.messaging.infrastructure.drivenadapters.jdbc;

import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One SQL Server container per test JVM, loaded once with the SDK's SQL Server reference DDL. Uses
 * azure-sql-edge, pinned to its final release, so it also runs on ARM hosts. Tests call {@link
 * #truncate()} in {@code @BeforeEach} to start from empty tables.
 */
final class SqlServerTestSupport {

  static final String REFERENCE_DDL = "db/migration/outbox/outbox_message_create.sql";

  private static final MSSQLServerContainer CONTAINER =
      new MSSQLServerContainer(
              DockerImageName.parse("mcr.microsoft.com/azure-sql-edge:1.0.7")
                  .asCompatibleSubstituteFor("mcr.microsoft.com/mssql/server"))
          .acceptLicense();
  private static DataSource dataSource;

  private SqlServerTestSupport() {}

  static synchronized DataSource dataSource() {
    if (dataSource == null) {
      CONTAINER.start();
      dataSource =
          new DriverManagerDataSource(
              CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
      new ResourceDatabasePopulator(new ClassPathResource(REFERENCE_DDL)).execute(dataSource);
    }
    return dataSource;
  }

  static void truncate() {
    JdbcClient.create(dataSource()).sql("TRUNCATE TABLE outbox_message").update();
  }
}
