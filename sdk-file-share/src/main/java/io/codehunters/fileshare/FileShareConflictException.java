package io.codehunters.fileshare;

/** Thrown when the requested operation conflicts with the file's current state (HTTP 409). */
public class FileShareConflictException extends FileShareException {

  /**
   * Creates an exception with a message.
   *
   * @param message the detail message
   */
  public FileShareConflictException(String message) {
    super(message);
  }

  /**
   * Creates an exception for an HTTP error response.
   *
   * @param message the detail message
   * @param status the HTTP status the file-share service answered with
   */
  public FileShareConflictException(String message, int status) {
    super(message, status);
  }
}
