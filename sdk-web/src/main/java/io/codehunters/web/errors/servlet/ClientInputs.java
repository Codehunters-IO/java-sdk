package io.codehunters.web.errors.servlet;

import io.codehunters.commons.error.ClientInputException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpStatus;

/**
 * Reads sdk-commons' {@link ClientInputException}. Only loaded when sdk-commons is on the
 * classpath, so the handler works without it; no other class of sdk-web references sdk-commons.
 */
final class ClientInputs {

  private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;

  private ClientInputs() {}

  /**
   * A 400 problem for the first {@link ClientInputException} in the cause chain. Its detail is the
   * bundle message {@code error.<code>}, else the exception message, safe by the exception's
   * contract.
   */
  static Optional<ProblemSpec> problem(Throwable exception, int maxDepth) {
    Throwable current = exception;
    for (int depth = 0; current != null && depth < maxDepth; depth++) {
      if (current instanceof ClientInputException rejected) {
        return Optional.of(spec(rejected));
      }
      current = current.getCause();
    }
    return Optional.empty();
  }

  private static ProblemSpec spec(ClientInputException rejected) {
    String code = rejected.code();
    String detail =
        Objects.requireNonNullElse(rejected.getMessage(), StatusCodes.reasonPhrase(STATUS));
    return new ProblemSpec(STATUS, code, "error." + code, List.of(), detail, List.of());
  }
}
