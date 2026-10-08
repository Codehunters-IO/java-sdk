package io.codehunters.weberrors.web.fixture;

import io.codehunters.weberrors.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** An application's own advice, which must take precedence over the SDK's. */
@RestControllerAdvice
public class ApplicationAdvice {

  @ExceptionHandler(ApiException.class)
  ResponseEntity<String> handle(ApiException exception) {
    return ResponseEntity.status(HttpStatus.I_AM_A_TEAPOT).body("handled by the application");
  }
}
