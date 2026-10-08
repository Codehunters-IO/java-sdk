package io.codehunters.weberrors.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.context.request.ServletWebRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes a problem detail straight to the servlet response, for code that runs outside Spring MVC
 * such as Spring Security's entry points.
 */
public final class ProblemWriter {

  private final ProblemFactory problems;

  /**
   * Creates the writer.
   *
   * @param messages resolves the localized details
   * @param traceIds supplies the {@code traceId} extension
   * @param settings how the problem details are built
   */
  public ProblemWriter(MessageSource messages, TraceIdProvider traceIds, ProblemSettings settings) {
    this.problems = new ProblemFactory(messages, traceIds, settings);
  }

  /**
   * Writes the problem for a status, unless the response is already committed.
   *
   * @param status the status to answer with
   * @param request the request being answered
   * @param response the response to write
   * @throws IOException when the response cannot be written
   */
  public void write(HttpStatusCode status, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    if (response.isCommitted()) {
      return;
    }
    var webRequest = new ServletWebRequest(request, response);
    ProblemDetail problem = problems.create(ProblemSpec.ofStatus(status), webRequest);
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    JsonMapper.shared().writeValue(response.getOutputStream(), members(problem));
  }

  /**
   * The members as Spring MVC renders them, with the extensions at the top level. JSON is UTF-8 by
   * definition, so no charset parameter is added, matching Spring MVC's own problem responses.
   */
  private static Map<String, Object> members(ProblemDetail problem) {
    var members = new LinkedHashMap<String, Object>();
    if (problem.getType() != null) {
      members.put("type", problem.getType().toString());
    }
    members.put("title", problem.getTitle());
    members.put("status", problem.getStatus());
    members.put("detail", problem.getDetail());
    if (problem.getInstance() != null) {
      members.put("instance", problem.getInstance().toString());
    }
    if (problem.getProperties() != null) {
      members.putAll(problem.getProperties());
    }
    return members;
  }
}
