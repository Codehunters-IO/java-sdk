package io.codehunters.fileshare.domain.exception;

/** Thrown when the file-share service is unreachable or returns a 5xx response. */
public class FileShareUnavailableException extends FileShareException {

  /**
   * Creates an exception with a message.
   *
   * @param message the detail message
   */
  public FileShareUnavailableException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and underlying cause.
   *
   * @param message the detail message
   * @param cause the underlying cause
   */
  public FileShareUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
