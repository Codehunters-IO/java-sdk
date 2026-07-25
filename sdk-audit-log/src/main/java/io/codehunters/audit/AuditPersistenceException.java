package io.codehunters.audit;

/** Thrown when persisting an audit event fails and fail-on-error is enabled. */
public class AuditPersistenceException extends RuntimeException {

  /**
   * Creates the exception with a message and cause.
   *
   * @param message the detail message
   * @param cause the underlying cause
   */
  public AuditPersistenceException(String message, Throwable cause) {
    super(message, cause);
  }
}
