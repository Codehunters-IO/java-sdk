package io.codehunters.security.authz.servlet;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;

/**
 * The request as Keycloak's enforcer sees it.
 *
 * <p>Keycloak matches its patterns against the raw request URI, while Spring MVC routes on the path
 * decoded segment by segment, without path parameters: {@code /api/%6Frders/1} would reach {@code
 * /api/orders/{id}} without matching its mapping. The URI is therefore the path as Spring MVC
 * matches it. Paths whose decoding is ambiguous (encoded separators, dots, percents or semicolons)
 * are refused before Keycloak is asked, see {@link #isAmbiguous(HttpServletRequest)}.
 *
 * <p>The authorization header carries the token being enforced, the one Spring Security validated:
 * Keycloak asks for permissions on the mapped resource only when it sees a bearer authorization
 * header.
 */
final class EnforcedRequest extends HttpServletRequestWrapper {

  // Encoded '/', '\', '.', '%', ';' and plain ';' decode differently in Keycloak and Spring MVC.
  private static final Pattern AMBIGUOUS = Pattern.compile("(?i)%2f|%5c|%2e|%25|%3b|;");

  private final String authorization;

  EnforcedRequest(HttpServletRequest request, String token) {
    super(request);
    this.authorization = "Bearer " + token;
  }

  /**
   * Whether the raw path holds sequences whose decoded form could route somewhere Keycloak's
   * pattern for the path does not cover.
   */
  static boolean isAmbiguous(HttpServletRequest request) {
    return AMBIGUOUS.matcher(rawUri(request)).find();
  }

  @Override
  public String getRequestURI() {
    var request = (HttpServletRequest) getRequest();
    return contextPath(request) + matchedPath(request);
  }

  @Override
  public String getHeader(String name) {
    return isAuthorization(name) ? authorization : super.getHeader(name);
  }

  @Override
  public Enumeration<String> getHeaders(String name) {
    return isAuthorization(name)
        ? Collections.enumeration(List.of(authorization))
        : super.getHeaders(name);
  }

  private static String matchedPath(HttpServletRequest request) {
    return RequestPath.parse(rawUri(request), contextPath(request))
        .pathWithinApplication()
        .elements()
        .stream()
        .map(EnforcedRequest::valueToMatch)
        .collect(Collectors.joining());
  }

  private static String valueToMatch(PathContainer.Element element) {
    return element instanceof PathContainer.PathSegment segment
        ? segment.valueToMatch()
        : element.value();
  }

  // An include keeps the including request's URI; the included one is in the request attributes.
  private static String rawUri(HttpServletRequest request) {
    return included(request, RequestDispatcher.INCLUDE_REQUEST_URI, request.getRequestURI());
  }

  private static String contextPath(HttpServletRequest request) {
    return included(request, RequestDispatcher.INCLUDE_CONTEXT_PATH, request.getContextPath());
  }

  private static String included(HttpServletRequest request, String attribute, String fallback) {
    return request.getDispatcherType() == DispatcherType.INCLUDE
            && request.getAttribute(attribute) instanceof String value
        ? value
        : fallback;
  }

  private static boolean isAuthorization(String name) {
    return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name);
  }
}
