package io.codehunters.messaging.infrastructure.drivenadapters.jdbc;

import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One SQL Server container per test JVM, loaded once with the SDK's SQL Server reference DDL. Uses
 * azure-sql-edge, pinned to its final release, so it also runs on ARM hosts. Tests call {@link
 * #truncate()} in {@code @BeforeEach} to start from empty tables.
 */
final class SqlServerTestSupport {

  static final String REFERENCE_DDL = "db/migration/outbox/outbox_message_create.sql";
  static final String UPGRADE_DDL =
      "db/migration/outbox/sqlserver/outbox_message_add_send_started_at.sql";

  private static final String UPGRADE_DATABASE = "upgraded_from_6x";

  private static final MSSQLServerContainer CONTAINER =
      new MSSQLServerContainer(
              DockerImageName.parse("mcr.microsoft.com/azure-sql-edge:1.0.7")
                  .asCompatibleSubstituteFor("mcr.microsoft.com/mssql/server"))
          .acceptLicense();
  private static DataSource dataSource;
  private static DataSource upgradedDataSource;

  private SqlServerTestSupport() {}

  static synchronized DataSource dataSource() {
    if (dataSource == null) {
      CONTAINER.start();
      dataSource =
          new DriverManagerDataSource(
              CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
      loadReferenceDdl(dataSource);
    }
    return dataSource;
  }

  /**
   * A separate database shaped like a 6.x install: the reference DDL without {@code
   * send_started_at}, then the 7.0 upgrade script applied twice to prove it is idempotent.
   */
  static synchronized DataSource upgradedDataSource() {
    if (upgradedDataSource == null) {
      JdbcClient.create(dataSource()).sql("CREATE DATABASE " + UPGRADE_DATABASE).update();
      DataSource upgraded =
          new DriverManagerDataSource(
              CONTAINER.getJdbcUrl() + ";databaseName=" + UPGRADE_DATABASE,
              CONTAINER.getUsername(),
              CONTAINER.getPassword());
      loadReferenceDdl(upgraded);
      JdbcClient.create(upgraded)
          .sql("ALTER TABLE outbox_message DROP COLUMN send_started_at")
          .update();
      runScript(upgraded, UPGRADE_DDL);
      runScript(upgraded, UPGRADE_DDL);
      upgradedDataSource = upgraded;
    }
    return upgradedDataSource;
  }

  private static void loadReferenceDdl(DataSource target) {
    runScript(target, REFERENCE_DDL);
  }

  // The SDK's T-SQL scripts are single batches without statement separators: run each one whole.
  private static void runScript(DataSource target, String script) {
    var populator = new ResourceDatabasePopulator(new ClassPathResource(script));
    populator.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
    populator.execute(target);
  }

  static void truncate() {
    JdbcClient.create(dataSource()).sql("TRUNCATE TABLE outbox_message").update();
  }

  static void truncateUpgraded() {
    JdbcClient.create(upgradedDataSource()).sql("TRUNCATE TABLE outbox_message").update();
  }
}
