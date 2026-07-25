package io.codehunters.fileshare.domain.exception;

/**
 * Base type for all file-share client failures. More specific subtypes map distinct error
 * conditions (not found, unauthorized, conflict, validation, service unavailable); this base type
 * is thrown for unexpected or unclassified failures.
 */
public class FileShareException extends RuntimeException {

  /**
   * Creates an exception with a message.
   *
   * @param message the detail message
   */
  public FileShareException(String message) {
    super(message);
  }

  /**
   * Creates an exception with a message and underlying cause.
   *
   * @param message the detail message
   * @param cause the underlying cause
   */
  public FileShareException(String message, Throwable cause) {
    super(message, cause);
  }
}
