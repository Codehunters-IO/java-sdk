package io.codehunters.parameter;

/**
 * Thrown when the underlying database access fails for any reason. Wraps Spring {@code
 * DataAccessException}.
 */
public class ParameterAccessException extends ParameterException {

  /**
   * Creates an access exception with a message and cause.
   *
   * @param message the failure description
   * @param cause the underlying database error
   */
  public ParameterAccessException(String message, Throwable cause) {
    super(message, cause);
  }
}
