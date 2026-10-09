package io.codehunters.web.errors.servlet.fixture;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** An application exception mapped to a status by annotation, as Spring MVC allows. */
@ResponseStatus(HttpStatus.I_AM_A_TEAPOT)
public class TeapotException extends RuntimeException {

  /** Creates the exception with a message that must never reach the client. */
  public TeapotException() {
    super("teapot internal detail");
  }
}
