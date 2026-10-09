package io.codehunters.messaging.jdbc.config;

import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;

/**
 * Fails startup when the outbox data source is not PostgreSQL, the only database the outbox SQL
 * supports. Reads only the product name from the driver metadata, so the message never carries the
 * URL or credentials. A database that cannot be reached at startup is left to the first claim.
 */
final class OutboxDatabaseCheck {

  private static final Logger log = LoggerFactory.getLogger(OutboxDatabaseCheck.class);
  private static final String POSTGRESQL = "PostgreSQL";

  private OutboxDatabaseCheck() {}

  static void requirePostgres(DataSource dataSource) {
    String product = productName(dataSource);
    if (product != null && !POSTGRESQL.equals(product)) {
      throw new IllegalStateException(
          "The outbox supports PostgreSQL only, but the data source is " + product);
    }
  }

  private static String productName(DataSource dataSource) {
    try {
      return JdbcUtils.extractDatabaseMetaData(
          dataSource, DatabaseMetaData::getDatabaseProductName);
    } catch (MetaDataAccessException e) {
      log.warn(
          "Could not read the outbox database product at startup ({})", e.getClass().getName());
      return null;
    }
  }
}
