package io.codehunters.fileshare.domain.exception;

/** Thrown when the requested file does not exist (HTTP 404). */
public class FileShareNotFoundException extends FileShareException {

  /**
   * Creates an exception with a message.
   *
   * @param message the detail message
   */
  public FileShareNotFoundException(String message) {
    super(message);
  }
}
