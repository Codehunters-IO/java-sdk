package io.codehunters.parameter;

/** Base exception for all parameter SDK failures. */
public class ParameterException extends RuntimeException {

  /**
   * Creates an exception with a message.
   *
   * @param message the failure description
   */
  public ParameterException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and cause.
   *
   * @param message the failure description
   * @param cause the underlying cause
   */
  public ParameterException(String message, Throwable cause) {
    super(message, cause);
  }
}
