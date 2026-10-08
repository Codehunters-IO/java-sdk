package io.codehunters.weberrors.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * Answers the servlet container's error dispatch with a problem detail, in place of Boot's {@code
 * BasicErrorController}. It covers what never reaches Spring MVC: {@code sendError}, exceptions
 * thrown by filters and errors raised by the container. The dispatched exception is never exposed.
 */
@RestController
@RequestMapping("${server.error.path:${error.path:/error}}")
public class ProblemErrorController implements ErrorController {

  private final ProblemFactory problems;

  /**
   * Creates the controller.
   *
   * @param messages resolves the localized details
   * @param traceIds supplies the {@code traceId} extension
   * @param settings how the problem details are built
   */
  public ProblemErrorController(
      MessageSource messages, TraceIdProvider traceIds, ProblemSettings settings) {
    this.problems = new ProblemFactory(messages, traceIds, settings);
  }

  /**
   * Answers the error dispatch. The content type is fixed, so a client asking for HTML still gets
   * the problem instead of an empty body.
   *
   * @param request the error dispatch
   * @return the problem detail for the dispatched status
   */
  @RequestMapping
  public ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
    HttpStatusCode status = statusOf(request);
    var problem = problems.create(ProblemSpec.ofStatus(status), new ServletWebRequest(request));
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  private static HttpStatusCode statusOf(HttpServletRequest request) {
    Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    return status instanceof Integer value
        ? StatusCodes.errorStatus(value)
        : HttpStatus.INTERNAL_SERVER_ERROR;
  }
}
