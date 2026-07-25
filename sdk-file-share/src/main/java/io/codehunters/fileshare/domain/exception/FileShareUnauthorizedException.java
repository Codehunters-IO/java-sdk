package io.codehunters.fileshare.domain.exception;

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
}
