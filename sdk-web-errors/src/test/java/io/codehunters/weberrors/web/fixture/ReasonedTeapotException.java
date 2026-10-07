package io.codehunters.weberrors.web.fixture;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** An annotated application exception that states its own client-facing reason. */
@ResponseStatus(code = HttpStatus.I_AM_A_TEAPOT, reason = "Only tea here")
public class ReasonedTeapotException extends RuntimeException {

  /** Creates the exception. */
  public ReasonedTeapotException() {
    super("internal detail");
  }
}
