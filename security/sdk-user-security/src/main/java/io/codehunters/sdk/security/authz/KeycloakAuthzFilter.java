package io.codehunters.sdk.security.authz;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Policy Enforcement Point (PEP) shared across every {@code vitxo-ms}. Delegates the allow/deny
 * decision to Keycloak Authorization Services (Resources x Scopes x Policies x Permissions) for
 * every request matching {@link KeycloakPolicyEnforcerProperties#getPaths()}.
 *
 * <p>See ADR-0008 (vitxo-ms-auth) for the decision to centralize this filter in the SDK instead of
 * duplicating it per service.
 */
public class KeycloakAuthzFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(KeycloakAuthzFilter.class);
  private static final String BEARER_PREFIX = "Bearer ";

  private final AuthzClient authzClient;
  private final KeycloakPolicyEnforcerProperties props;
  private final AntPathMatcher matcher = new AntPathMatcher();
  private final UrlPathHelper pathHelper = new UrlPathHelper();

  /**
   * Creates the filter.
   *
   * @param authzClient client used to query Keycloak Authorization Services
   * @param props the path-to-resource/scope catalog for this service
   */
  public KeycloakAuthzFilter(AuthzClient authzClient, KeycloakPolicyEnforcerProperties props) {
    this.authzClient = authzClient;
    this.props = props;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    // Match on the decoded, normalized path within the application (context-path stripped), the
    // same convention the policy-enforcer `paths` catalog is written in. Using getServletPath()
    // here was fragile: some servlet mappings leave it empty, which would silently match nothing
    // and let a protected endpoint through unenforced.
    var match = matchRequest(pathHelper.getPathWithinApplication(request), request.getMethod());
    if (match.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }

    var token = extractBearer(request);
    if (token == null) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token");
      return;
    }

    try {
      var authzRequest = new AuthorizationRequest();
      match
          .get()
          .scopes()
          .forEach(scope -> authzRequest.addPermission(match.get().resource(), scope));
      authzClient.authorization(token).authorize(authzRequest);
    } catch (Exception ex) {
      log.warn(
          "Authorization denied: method={} path={} resource={} cause={}",
          request.getMethod(),
          request.getRequestURI(),
          match.get().resource(),
          ex.getMessage());
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
      return;
    }

    chain.doFilter(request, response);
  }

  private Optional<Match> matchRequest(String path, String method) {
    return props.getPaths().stream()
        .filter(p -> matcher.match(p.getPath(), path))
        .flatMap(
            p ->
                p.getMethods().stream()
                    .filter(m -> m.getMethod().equalsIgnoreCase(method))
                    .map(m -> new Match(p.getName(), m.getScopes())))
        .findFirst();
  }

  private String extractBearer(HttpServletRequest request) {
    var header = request.getHeader("Authorization");
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      return null;
    }
    return header.substring(BEARER_PREFIX.length()).trim();
  }

  private record Match(String resource, List<String> scopes) {}
}
