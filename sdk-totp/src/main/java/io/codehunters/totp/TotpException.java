package io.codehunters.totp;

/** Thrown when TOTP generation, verification or secret handling fails. */
public class TotpException extends RuntimeException {

  /**
   * Creates an exception with a message.
   *
   * @param message the detail message
   */
  public TotpException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and underlying cause.
   *
   * @param message the detail message
   * @param cause the underlying cause
   */
  public TotpException(String message, Throwable cause) {
    super(message, cause);
  }
}
