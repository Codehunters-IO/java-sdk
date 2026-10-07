package io.codehunters.fileshare.domain.exception;

/** Thrown when the request is rejected as invalid by the file-share service (HTTP 4xx). */
public class FileShareValidationException extends FileShareException {

  /**
   * Creates an exception with a message.
   *
   * @param message the detail message
   */
  public FileShareValidationException(String message) {
    super(message);
  }

  /**
   * Creates an exception for an HTTP error response.
   *
   * @param message the detail message
   * @param status the HTTP status the file-share service answered with
   */
  public FileShareValidationException(String message, int status) {
    super(message, status);
  }
}
