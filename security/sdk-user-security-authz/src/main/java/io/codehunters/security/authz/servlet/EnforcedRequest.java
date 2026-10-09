package io.codehunters.security.authz.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UrlPathHelper;

/**
 * The request as Keycloak's enforcer sees it.
 *
 * <p>Keycloak matches its patterns against the raw request URI, while Spring MVC routes on the
 * decoded path without path parameters: {@code /api/%6Frders/1} would reach {@code
 * /api/orders/{id}} without matching its mapping. The URI is therefore the decoded, cleaned path.
 *
 * <p>The authorization header carries the token being enforced, wherever the application's resolver
 * read it from: Keycloak asks for permissions on the mapped resource only when it sees a bearer
 * authorization header.
 */
final class EnforcedRequest extends HttpServletRequestWrapper {

  private static final UrlPathHelper PATHS = decodingPathHelper();

  private final String authorization;

  EnforcedRequest(HttpServletRequest request, String token) {
    super(request);
    this.authorization = "Bearer " + token;
  }

  @Override
  public String getRequestURI() {
    return getContextPath() + PATHS.getPathWithinApplication((HttpServletRequest) getRequest());
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

  private static boolean isAuthorization(String name) {
    return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name);
  }

  private static UrlPathHelper decodingPathHelper() {
    var helper = new UrlPathHelper();
    helper.setUrlDecode(true);
    helper.setRemoveSemicolonContent(true);
    return helper;
  }
}
