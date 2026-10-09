package io.codehunters.web.errors.security;

import io.codehunters.web.errors.servlet.ProblemWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Answers Spring Security's access denials with a problem detail. A challenge handler runs first to
 * set headers such as {@code WWW-Authenticate: Bearer error="insufficient_scope"}; the answer is
 * its error status, or 403.
 */
public final class ProblemAccessDeniedHandler implements AccessDeniedHandler {

  private final AccessDeniedHandler challenge;
  private final ProblemWriter writer;

  /**
   * Creates the handler.
   *
   * @param challenge sets the challenge headers, e.g. a {@code BearerTokenAccessDeniedHandler}
   * @param writer writes the problem detail
   */
  public ProblemAccessDeniedHandler(AccessDeniedHandler challenge, ProblemWriter writer) {
    this.challenge = challenge;
    this.writer = writer;
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException, ServletException {
    challenge.handle(request, response, exception);
    var status = ProblemAuthenticationEntryPoint.statusOf(response, HttpStatus.FORBIDDEN);
    writer.write(status, request, response);
  }
}
