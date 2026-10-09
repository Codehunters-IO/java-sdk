package io.codehunters.web.errors.servlet;

import io.codehunters.web.errors.FieldViolation;
import java.util.List;
import org.springframework.http.HttpStatusCode;

/**
 * What a problem detail must say, before localization.
 *
 * @param status the response status
 * @param code the stable error code
 * @param messageKey the {@code MessageSource} key of the detail
 * @param arguments the message arguments
 * @param defaultDetail the detail used when the key has no message
 * @param violations the failed constraints, empty when none
 */
record ProblemSpec(
    HttpStatusCode status,
    String code,
    String messageKey,
    List<Object> arguments,
    String defaultDetail,
    List<FieldViolation> violations) {

  /**
   * A problem named after its status, e.g. {@code FORBIDDEN} with key {@code web-errors.FORBIDDEN}.
   */
  static ProblemSpec ofStatus(HttpStatusCode status) {
    String code = StatusCodes.code(status);
    return new ProblemSpec(
        status, code, "web-errors." + code, List.of(), StatusCodes.reasonPhrase(status), List.of());
  }
}
