package io.codehunters.jpa.fixture;

import org.testcontainers.postgresql.PostgreSQLContainer;

/** One PostgreSQL container for the whole test JVM; Testcontainers stops it on exit. */
public final class PostgresDatabase {

  private static final PostgreSQLContainer CONTAINER = start();

  private PostgresDatabase() {}

  public static String jdbcUrl() {
    return CONTAINER.getJdbcUrl();
  }

  public static String username() {
    return CONTAINER.getUsername();
  }

  public static String password() {
    return CONTAINER.getPassword();
  }

  private static PostgreSQLContainer start() {
    var container = new PostgreSQLContainer("postgres:17-alpine");
    container.start();
    return container;
  }
}
