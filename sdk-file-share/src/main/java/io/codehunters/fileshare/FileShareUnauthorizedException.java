package io.codehunters.fileshare;

/** Thrown when the caller is not authenticated or not allowed to access the file (HTTP 401/403). */
public class FileShareUnauthorizedException extends FileShareException {

  /**
   * Creates an exception with a message.
   *
   * @param message the detail message
   */
  public FileShareUnauthorizedException(String message) {
    super(message);
  }

  /**
   * Creates an exception for an HTTP error response.
   *
   * @param message the detail message
   * @param status the HTTP status the file-share service answered with
   */
  public FileShareUnauthorizedException(String message, int status) {
    super(message, status);
  }

  /**
   * Creates an exception with a message and underlying cause.
   *
   * @param message the detail message
   * @param cause the underlying cause
   */
  public FileShareUnauthorizedException(String message, Throwable cause) {
    super(message, cause);
  }
}
