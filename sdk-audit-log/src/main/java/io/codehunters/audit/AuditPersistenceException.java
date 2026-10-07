package io.codehunters.audit;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Thrown when an audit event cannot be built or persisted and fail-on-error is enabled.
 *
 * <p>The SDK always throws a {@linkplain #redacted(String, Throwable) redacted} instance: database
 * messages can echo the row being written (Postgres, for example, reports {@code Failing row
 * contains (...)}), so neither the message nor the cause chain carries the original failure's text.
 * What stays is the failure's class, its SQLState and vendor code, and its stack trace.
 */
public class AuditPersistenceException extends RuntimeException {

  private final String failureClass;
  private final String sqlState;
  private final Integer vendorCode;

  /**
   * Creates the exception with a message and cause. The cause is kept as given, message included.
   *
   * @param message the detail message
   * @param cause the underlying cause
   */
  public AuditPersistenceException(String message, Throwable cause) {
    this(message, cause, Diagnostics.of(cause));
  }

  private AuditPersistenceException(String message, Throwable cause, Diagnostics diagnostics) {
    super(message, cause);
    this.failureClass = diagnostics.failureClass();
    this.sqlState = diagnostics.sqlState();
    this.vendorCode = diagnostics.vendorCode();
  }

  /**
   * Creates an exception that keeps only the class, SQLState, vendor code and stack trace of the
   * failure. Its cause has no message and no further cause.
   *
   * @param message the detail message, which must not contain event data
   * @param failure the failure to describe
   * @return the redacted exception
   * @since 3.0.0
   */
  public static AuditPersistenceException redacted(String message, Throwable failure) {
    Diagnostics diagnostics = Diagnostics.of(failure);
    return new AuditPersistenceException(
        message + ": " + diagnostics, new RedactedFailure(failure), diagnostics);
  }

  /**
   * Returns the class name of the failure.
   *
   * @return the fully qualified class name
   * @since 3.0.0
   */
  public String failureClass() {
    return failureClass;
  }

  /**
   * Returns the SQLState of the first {@link SQLException} in the failure's cause chain.
   *
   * @return the SQLState, or empty if the failure is not a database error
   * @since 3.0.0
   */
  public Optional<String> sqlState() {
    return Optional.ofNullable(sqlState);
  }

  /**
   * Returns the vendor error code of the first {@link SQLException} in the failure's cause chain.
   *
   * @return the vendor code, or empty if the failure is not a database error or the driver reports
   *     {@code 0} (Postgres always does)
   * @since 3.0.0
   */
  public Optional<Integer> vendorCode() {
    return Optional.ofNullable(vendorCode);
  }

  private record Diagnostics(String failureClass, String sqlState, Integer vendorCode) {

    static Diagnostics of(Throwable failure) {
      String failureClass = failure == null ? null : failure.getClass().getName();
      SQLException sql = firstSqlException(failure);
      if (sql == null) {
        return new Diagnostics(failureClass, null, null);
      }
      Integer vendorCode = sql.getErrorCode() == 0 ? null : sql.getErrorCode();
      return new Diagnostics(failureClass, sql.getSQLState(), vendorCode);
    }

    private static SQLException firstSqlException(Throwable failure) {
      for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
        if (cause instanceof SQLException sql) {
          return sql;
        }
      }
      return null;
    }

    @Override
    public String toString() {
      return "exception=" + failureClass + " sqlState=" + sqlState + " vendorCode=" + vendorCode;
    }
  }

  /** Stands in for the original failure: its stack trace, without its message or causes. */
  private static final class RedactedFailure extends RuntimeException {

    RedactedFailure(Throwable original) {
      super(original.getClass().getName(), null, false, true);
      setStackTrace(original.getStackTrace());
    }
  }
}
