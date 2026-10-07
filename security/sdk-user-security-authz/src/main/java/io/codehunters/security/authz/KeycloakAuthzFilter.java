package io.codehunters.security.authz;

import io.codehunters.security.authz.KeycloakPolicyEnforcerProperties.EnforcementMode;
import io.codehunters.security.authz.KeycloakPolicyEnforcerProperties.MethodMapping;
import io.codehunters.security.authz.KeycloakPolicyEnforcerProperties.PathMapping;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Supplier;
import org.keycloak.authorization.client.AuthorizationDeniedException;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.util.HttpResponseException;
import org.keycloak.representations.idm.authorization.AuthorizationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Policy enforcement point that delegates allow/deny decisions to Keycloak Authorization Services
 * for every request whose path is in the configured catalog.
 *
 * <p>The most specific matching pattern decides. A method the catalog does not list for that path
 * is denied, so adding a route never leaves it unprotected by omission. Keycloak denials answer
 * 403; failures to reach Keycloak answer 503.
 */
public class KeycloakAuthzFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(KeycloakAuthzFilter.class);
  private static final String BEARER_PREFIX = "bearer ";

  private final Supplier<AuthzClient> authzClient;
  private final KeycloakPolicyEnforcerProperties properties;
  private final AntPathMatcher matcher = new AntPathMatcher();
  private final UrlPathHelper pathHelper = new UrlPathHelper();

  /**
   * Creates the filter.
   *
   * @param authzClient supplies the Keycloak client; called on the first enforced request
   * @param properties the catalog and enforcement mode
   */
  public KeycloakAuthzFilter(
      Supplier<AuthzClient> authzClient, KeycloakPolicyEnforcerProperties properties) {
    this.authzClient = authzClient;
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return properties.enforcementMode() == EnforcementMode.DISABLED;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Optional<PathMapping> path = mostSpecificMatch(pathHelper.getPathWithinApplication(request));
    if (path.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }
    String token = bearerToken(request);
    if (token == null) {
      rejectAnonymous(request, response, chain, path.get());
      return;
    }
    Decision decision = decide(path.get(), request.getMethod(), token);
    if (decision == Decision.ALLOW || properties.enforcementMode() == EnforcementMode.PERMISSIVE) {
      chain.doFilter(request, response);
      return;
    }
    response.sendError(decision.status, decision.reason);
  }

  private void rejectAnonymous(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain, PathMapping path)
      throws ServletException, IOException {
    if (properties.enforcementMode() == EnforcementMode.PERMISSIVE) {
      log.warn("Permissive: request without token on resource={}", path.name());
      chain.doFilter(request, response);
      return;
    }
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing bearer token");
  }

  private Optional<PathMapping> mostSpecificMatch(String path) {
    Comparator<String> specificity = matcher.getPatternComparator(path);
    return properties.paths().stream()
        .filter(mapping -> matcher.match(mapping.path(), path))
        .min(Comparator.comparing(PathMapping::path, specificity));
  }

  private Decision decide(PathMapping path, String method, String token) {
    Optional<MethodMapping> mapping =
        path.methods().stream().filter(m -> m.method().equalsIgnoreCase(method)).findFirst();
    if (mapping.isEmpty()) {
      log.warn("Method not in policy catalog: method={} resource={}", method, path.name());
      return Decision.DENY;
    }
    return authorize(path.name(), mapping.get(), token, method);
  }

  private Decision authorize(String resource, MethodMapping mapping, String token, String method) {
    try {
      var request = new AuthorizationRequest();
      mapping.scopes().forEach(scope -> request.addPermission(resource, scope));
      authzClient.get().authorization(token).authorize(request);
      return Decision.ALLOW;
    } catch (RuntimeException e) {
      Decision decision = isDenial(e) ? Decision.DENY : Decision.UNAVAILABLE;
      log.warn(
          "Authorization {}: method={} resource={} cause={}",
          decision == Decision.DENY ? "denied" : "unavailable",
          method,
          resource,
          e.getClass().getSimpleName());
      return decision;
    }
  }

  /**
   * 401/403 are Keycloak's decisions; 400 means Keycloak does not know the resource or scope, which
   * is a catalog mistake that must fail closed rather than look like an outage.
   */
  private static boolean isDenial(RuntimeException e) {
    Throwable cause =
        e instanceof AuthorizationDeniedException && e.getCause() != null ? e.getCause() : e;
    if (!(cause instanceof HttpResponseException http)) {
      return false;
    }
    if (http.getStatusCode() == HttpServletResponse.SC_BAD_REQUEST) {
      log.error("Keycloak rejected the authorization request; check the resource and scopes");
    }
    return http.getStatusCode() == HttpServletResponse.SC_BAD_REQUEST
        || http.getStatusCode() == HttpServletResponse.SC_UNAUTHORIZED
        || http.getStatusCode() == HttpServletResponse.SC_FORBIDDEN;
  }

  private static String bearerToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header == null
        || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      return null;
    }
    return header.substring(BEARER_PREFIX.length()).trim();
  }

  private enum Decision {
    ALLOW(HttpServletResponse.SC_OK, ""),
    DENY(HttpServletResponse.SC_FORBIDDEN, "Forbidden"),
    UNAVAILABLE(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Authorization service unavailable");

    private final int status;
    private final String reason;

    Decision(int status, String reason) {
      this.status = status;
      this.reason = reason;
    }
  }
}
