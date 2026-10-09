package io.codehunters.messaging.jdbc;

import java.sql.DatabaseMetaData;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;

/**
 * Resolves the {@link OutboxDialect} from the {@code codehunters.outbox.claim.dialect} setting. The
 * value {@code auto} reads the database product name once; an unsupported product fails fast
 * instead of silently falling back to the generic dialect, which cannot claim atomically under
 * concurrency.
 *
 * <p>MySQL and MariaDB are not supported: the generic claim fails there at dispatch time (error
 * 1093 on the self-referencing update, then {@code FETCH FIRST} syntax). Both {@code auto} and
 * {@code generic} therefore check the product name and fail startup on them.
 */
public final class OutboxDialectResolver {

  private static final String PROPERTY = "codehunters.outbox.claim.dialect";
  private static final String AUTO = "auto";
  private static final String GENERIC = "generic";
  private static final Set<String> UNSUPPORTED_PRODUCTS = Set.of("MySQL", "MariaDB");

  private OutboxDialectResolver() {}

  /**
   * Resolves the claim dialect.
   *
   * @param dataSource the outbox data source, read only when {@code configured} is {@code auto}
   * @param configured {@code auto}, {@code postgresql}, {@code sqlserver} or {@code generic}
   *     (case-insensitive); {@code null} means {@code auto}
   * @return the resolved dialect
   * @throws IllegalArgumentException if {@code configured} is not a known value
   * @throws IllegalStateException if {@code auto} meets an unsupported or unreadable database, or
   *     {@code auto} or {@code generic} meets MySQL or MariaDB
   */
  public static OutboxDialect resolve(DataSource dataSource, String configured) {
    String value = Objects.requireNonNullElse(configured, AUTO).trim().toLowerCase(Locale.ROOT);
    if (AUTO.equals(value)) {
      return fromProductName(rejectUnsupported(readProductName(dataSource)));
    }
    if (GENERIC.equals(value)) {
      tryReadProductName(dataSource).ifPresent(OutboxDialectResolver::rejectUnsupported);
    }
    return fromConfigured(value);
  }

  private static String rejectUnsupported(String productName) {
    if (UNSUPPORTED_PRODUCTS.contains(productName)) {
      throw new IllegalStateException(
          ("%s is not supported by the outbox claim: the generic dialect fails there at dispatch"
                  + " time; use PostgreSQL or SQL Server (%s)")
              .formatted(productName, PROPERTY));
    }
    return productName;
  }

  // An explicit generic dialect keeps working when the metadata cannot be read.
  private static Optional<String> tryReadProductName(DataSource dataSource) {
    try {
      return Optional.ofNullable(readProductName(dataSource));
    } catch (IllegalStateException e) {
      return Optional.empty();
    }
  }

  private static OutboxDialect fromConfigured(String value) {
    return switch (value) {
      case "postgresql" -> new PostgresOutboxDialect();
      case "sqlserver" -> new SqlServerOutboxDialect();
      case GENERIC -> new GenericOutboxDialect();
      default ->
          throw new IllegalArgumentException(
              "Unknown %s '%s'; use auto, postgresql, sqlserver or generic"
                  .formatted(PROPERTY, value));
    };
  }

  private static OutboxDialect fromProductName(String productName) {
    return switch (productName) {
      case "PostgreSQL" -> new PostgresOutboxDialect();
      case "Microsoft SQL Server" -> new SqlServerOutboxDialect();
      case "H2" -> new GenericOutboxDialect();
      default ->
          throw new IllegalStateException(
              "Unsupported database '" + productName + "' for the outbox claim; set " + PROPERTY);
    };
  }

  private static String readProductName(DataSource dataSource) {
    try {
      return JdbcUtils.extractDatabaseMetaData(
          dataSource, DatabaseMetaData::getDatabaseProductName);
    } catch (MetaDataAccessException e) {
      throw new IllegalStateException(
          "Cannot read the database product name (%s); set %s"
              .formatted(e.getClass().getName(), PROPERTY));
    }
  }
}
