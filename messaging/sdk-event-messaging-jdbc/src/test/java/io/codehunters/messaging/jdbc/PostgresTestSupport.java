package io.codehunters.messaging.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL 17 container per test JVM, loaded once with the SDK's Postgres reference DDL.
 * Tests call {@link #truncate()} in {@code @BeforeEach} to start from empty tables.
 */
final class PostgresTestSupport {

  static final String REFERENCE_DDL = "db/migration/outbox/postgresql/outbox_message_create.sql";
  static final String UPGRADE_DDL =
      "db/migration/outbox/postgresql/outbox_message_add_send_started_at.sql";
  static final String TRACEPARENT_UPGRADE_DDL =
      "db/migration/outbox/postgresql/outbox_message_add_traceparent.sql";

  private static final String UPGRADE_SCHEMA = "upgraded_from_6x";

  private static final PostgreSQLContainer CONTAINER =
      new PostgreSQLContainer("postgres:17-alpine");
  private static DataSource dataSource;
  private static DataSource pooledDataSource;
  private static DataSource upgradedDataSource;

  private PostgresTestSupport() {}

  static synchronized DataSource dataSource() {
    if (dataSource == null) {
      CONTAINER.start();
      dataSource = createDataSource();
      loadReferenceDdl(dataSource);
    }
    return dataSource;
  }

  /**
   * A connection pool on the reference schema, for tests that measure throughput or latency: the
   * plain data source opens a connection per statement.
   */
  static synchronized DataSource pooledDataSource() {
    if (pooledDataSource == null) {
      dataSource();
      var pool = new HikariDataSource();
      pool.setJdbcUrl(CONTAINER.getJdbcUrl());
      pool.setUsername(CONTAINER.getUsername());
      pool.setPassword(CONTAINER.getPassword());
      pool.setMaximumPoolSize(4);
      pooledDataSource = pool;
    }
    return pooledDataSource;
  }

  static void truncate() {
    JdbcClient.create(dataSource()).sql("TRUNCATE outbox_message, shedlock").update();
  }

  /**
   * A separate schema shaped like a 6.x install: the reference DDL without {@code send_started_at}
   * and {@code traceparent}, then the 7.0 and 9.0 upgrade scripts, each applied twice to prove it
   * is idempotent.
   */
  static synchronized DataSource upgradedDataSource() {
    if (upgradedDataSource == null) {
      JdbcClient.create(dataSource()).sql("CREATE SCHEMA " + UPGRADE_SCHEMA).update();
      DataSource upgraded = createDataSource(UPGRADE_SCHEMA);
      loadReferenceDdl(upgraded);
      JdbcClient.create(upgraded)
          .sql("ALTER TABLE outbox_message DROP COLUMN send_started_at, DROP COLUMN traceparent")
          .update();
      runScript(upgraded, UPGRADE_DDL);
      runScript(upgraded, UPGRADE_DDL);
      runScript(upgraded, TRACEPARENT_UPGRADE_DDL);
      runScript(upgraded, TRACEPARENT_UPGRADE_DDL);
      upgradedDataSource = upgraded;
    }
    return upgradedDataSource;
  }

  static void truncateUpgraded() {
    JdbcClient.create(upgradedDataSource()).sql("TRUNCATE outbox_message, shedlock").update();
  }

  private static DataSource createDataSource() {
    return createDataSource("public");
  }

  // The container URL may already carry query parameters, so the schema goes in the properties.
  private static DataSource createDataSource(String schema) {
    var ds = new DriverManagerDataSource();
    ds.setDriverClassName("org.postgresql.Driver");
    ds.setUrl(CONTAINER.getJdbcUrl());
    ds.setSchema(schema);
    ds.setUsername(CONTAINER.getUsername());
    ds.setPassword(CONTAINER.getPassword());
    return ds;
  }

  private static void loadReferenceDdl(DataSource target) {
    runScript(target, REFERENCE_DDL);
  }

  private static void runScript(DataSource target, String script) {
    new ResourceDatabasePopulator(new ClassPathResource(script)).execute(target);
  }
}
