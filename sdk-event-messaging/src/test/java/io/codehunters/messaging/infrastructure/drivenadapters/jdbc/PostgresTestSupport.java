package io.codehunters.messaging.infrastructure.drivenadapters.jdbc;

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

  private static final PostgreSQLContainer CONTAINER =
      new PostgreSQLContainer("postgres:17-alpine");
  private static DataSource dataSource;

  private PostgresTestSupport() {}

  static synchronized DataSource dataSource() {
    if (dataSource == null) {
      CONTAINER.start();
      dataSource = createDataSource();
      loadReferenceDdl(dataSource);
    }
    return dataSource;
  }

  static void truncate() {
    JdbcClient.create(dataSource()).sql("TRUNCATE outbox_message, shedlock").update();
  }

  private static DataSource createDataSource() {
    var ds = new DriverManagerDataSource();
    ds.setDriverClassName("org.postgresql.Driver");
    ds.setUrl(CONTAINER.getJdbcUrl());
    ds.setUsername(CONTAINER.getUsername());
    ds.setPassword(CONTAINER.getPassword());
    return ds;
  }

  private static void loadReferenceDdl(DataSource target) {
    new ResourceDatabasePopulator(new ClassPathResource(REFERENCE_DDL)).execute(target);
  }
}
