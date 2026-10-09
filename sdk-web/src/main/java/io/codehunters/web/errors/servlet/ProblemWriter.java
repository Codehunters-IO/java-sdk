package io.codehunters.web.errors.servlet;

import io.codehunters.web.errors.trace.TraceIdProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes a problem detail straight to the servlet response, for code that runs outside Spring MVC
 * such as Spring Security's entry points.
 *
 * <p>Spring MVC has not resolved the locale yet when such code runs: {@code LocaleContextHolder}
 * then holds the container's {@code Accept-Language} locale. Given the application's {@link
 * LocaleResolver}, the writer asks it instead, so the detail follows the same rules (e.g. {@code
 * X-Locale}) as the rest of the application.
 */
public final class ProblemWriter {

  private final ProblemFactory problems;
  private final Function<HttpServletRequest, Locale> locales;

  /**
   * Creates a writer that resolves details in the current {@code LocaleContextHolder} locale.
   *
   * @param messages resolves the localized details
   * @param traceIds supplies the {@code traceId} extension
   * @param settings how the problem details are built
   */
  public ProblemWriter(MessageSource messages, TraceIdProvider traceIds, ProblemSettings settings) {
    this(messages, traceIds, settings, request -> LocaleContextHolder.getLocale());
  }

  /**
   * Creates a writer that resolves details in the locale the application's resolver picks.
   *
   * @param messages resolves the localized details
   * @param traceIds supplies the {@code traceId} extension
   * @param settings how the problem details are built
   * @param localeResolver the application's {@code localeResolver}
   */
  public ProblemWriter(
      MessageSource messages,
      TraceIdProvider traceIds,
      ProblemSettings settings,
      LocaleResolver localeResolver) {
    this(messages, traceIds, settings, localeResolver::resolveLocale);
  }

  private ProblemWriter(
      MessageSource messages,
      TraceIdProvider traceIds,
      ProblemSettings settings,
      Function<HttpServletRequest, Locale> locales) {
    this.problems = new ProblemFactory(messages, traceIds, settings);
    this.locales = locales;
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
    ProblemDetail problem =
        problems.create(ProblemSpec.ofStatus(status), webRequest, locales.apply(request));
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
