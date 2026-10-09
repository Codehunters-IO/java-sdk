package io.codehunters.web.errors.servlet.fixture;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** A server-side conflict whose cause happens to be a rejected client input. */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param cause the cause
   */
  public ConflictException(Throwable cause) {
    super("conflict", cause);
  }
}
