package io.codehunters.fileshare.domain.exception;

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
}
