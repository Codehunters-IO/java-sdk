package io.codehunters.web.errors.security;

import io.codehunters.web.errors.servlet.ProblemWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Answers Spring Security's authentication failures with a problem detail. A challenge entry point
 * runs first to set headers such as {@code WWW-Authenticate}, and its status is kept when it sets
 * an error status; otherwise the answer is 401.
 */
public final class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final AuthenticationEntryPoint challenge;
  private final ProblemWriter writer;

  /**
   * Creates the entry point.
   *
   * @param challenge sets the challenge headers, e.g. a {@code BearerTokenAuthenticationEntryPoint}
   * @param writer writes the problem detail
   */
  public ProblemAuthenticationEntryPoint(AuthenticationEntryPoint challenge, ProblemWriter writer) {
    this.challenge = challenge;
    this.writer = writer;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException, ServletException {
    challenge.commence(request, response, exception);
    writer.write(statusOf(response, HttpStatus.UNAUTHORIZED), request, response);
  }

  static HttpStatusCode statusOf(HttpServletResponse response, HttpStatus fallback) {
    int status = response.getStatus();
    return status >= 400 && status <= 599 ? HttpStatusCode.valueOf(status) : fallback;
  }
}
