package io.codehunters.messaging.jdbc.config;

import java.sql.DatabaseMetaData;
import java.util.Optional;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;

/**
 * Fails startup when the outbox data source is not PostgreSQL, the only database the outbox SQL
 * supports. Reads only the product name from the driver metadata, so the message never carries the
 * URL or credentials. A database that cannot be reached at startup is left to the first claim.
 *
 * <p>This checks the JDBC driver, not the server: pgjdbc reports {@code PostgreSQL} for every
 * server it talks to. Amazon Redshift, CockroachDB or YugabyteDB reached through pgjdbc pass the
 * check but may fail at the first claim, which needs {@code FOR UPDATE ... SKIP LOCKED}. A vendor
 * driver that reports another name, such as EDB's ({@code EnterpriseDB}), is rejected.
 */
@Slf4j
final class OutboxDatabaseCheck {

  private static final String POSTGRESQL = "PostgreSQL";

  private OutboxDatabaseCheck() {}

  static void requirePostgres(DataSource dataSource) {
    productName(dataSource)
        .filter(product -> !POSTGRESQL.equals(product))
        .ifPresent(
            product -> {
              throw new IllegalStateException(
                  "The outbox supports PostgreSQL only, but the data source is " + product);
            });
  }

  private static Optional<String> productName(DataSource dataSource) {
    try {
      return Optional.ofNullable(
          JdbcUtils.extractDatabaseMetaData(dataSource, DatabaseMetaData::getDatabaseProductName));
    } catch (MetaDataAccessException e) {
      log.warn(
          "Could not read the outbox database product at startup ({})", e.getClass().getName());
      return Optional.empty();
    }
  }
}
