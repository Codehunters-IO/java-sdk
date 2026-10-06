package io.codehunters.fileshare.domain.exception;

import java.util.OptionalInt;

/**
 * Base type for all file-share client failures. More specific subtypes map distinct error
 * conditions (not found, unauthorized, conflict, validation, service unavailable); this base type
 * is thrown for unexpected or unclassified failures, including responses the client cannot read.
 */
public class FileShareException extends RuntimeException {

  private final int status;

  /**
   * Creates an exception with a message.
   *
   * @param message the detail message
   */
  public FileShareException(String message) {
    this(message, 0, null);
  }

  /**
   * Creates an exception with a message and underlying cause.
   *
   * @param message the detail message
   * @param cause the underlying cause
   */
  public FileShareException(String message, Throwable cause) {
    this(message, 0, cause);
  }

  /**
   * Creates an exception for an HTTP error response.
   *
   * @param message the detail message
   * @param status the HTTP status the file-share service answered with
   */
  public FileShareException(String message, int status) {
    this(message, status, null);
  }

  private FileShareException(String message, int status, Throwable cause) {
    super(message, cause);
    this.status = status;
  }

  /**
   * The HTTP status of the error response, when the failure came from one.
   *
   * @return the status, or empty for transport and client-side failures
   */
  public OptionalInt status() {
    return status == 0 ? OptionalInt.empty() : OptionalInt.of(status);
  }
}
