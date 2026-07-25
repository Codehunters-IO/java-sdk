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
}
