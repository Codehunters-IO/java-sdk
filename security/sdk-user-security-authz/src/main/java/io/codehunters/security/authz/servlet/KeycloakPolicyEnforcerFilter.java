package io.codehunters.security.authz.servlet;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.apache.http.client.HttpClient;
import org.keycloak.adapters.authorization.PolicyEnforcer;
import org.keycloak.adapters.authorization.integration.jakarta.ServletPolicyEnforcerFilter;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Keycloak's official {@link ServletPolicyEnforcerFilter}, adapted to run inside the Spring
 * Security filter chain after authorization.
 *
 * <p>Requests Spring Security left unauthenticated (public and excluded paths) are not enforced:
 * protected paths already answered 401 before reaching this filter. Error dispatches are not
 * enforced again. The bearer token is read with Spring Security's own resolver, so the enforcer
 * sees the token that was authenticated. The Keycloak enforcer is built on the first enforced
 * request; when Keycloak cannot be reached then, the request answers 503 and the next one retries.
 */
public class KeycloakPolicyEnforcerFilter extends ServletPolicyEnforcerFilter {

  private static final Logger log = LoggerFactory.getLogger(KeycloakPolicyEnforcerFilter.class);

  private final HttpClient httpClient;
  private final BearerTokenResolver tokens = new DefaultBearerTokenResolver();
  private final AuthenticationTrustResolver trust = new AuthenticationTrustResolverImpl();

  /**
   * Creates the filter.
   *
   * @param config the enforcer configuration, shared by every request
   * @param httpClient client for calls to Keycloak; {@code null} uses Keycloak's default, which has
   *     no timeouts
   */
  public KeycloakPolicyEnforcerFilter(PolicyEnforcerConfig config, HttpClient httpClient) {
    super(request -> config);
    this.httpClient = httpClient;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!isEnforceable((HttpServletRequest) request)) {
      chain.doFilter(request, response);
      return;
    }
    try {
      super.doFilter(request, response, chain);
    } catch (EnforcerUnavailableException e) {
      log.warn("Keycloak policy enforcer unavailable: {}", e.getCause().getMessage());
      ((HttpServletResponse) response)
          .sendError(
              HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Authorization service unavailable");
    }
  }

  @Override
  protected String extractBearerToken(HttpServletRequest request) {
    return tokens.resolve(request);
  }

  @Override
  protected PolicyEnforcer createPolicyEnforcer(
      HttpServletRequest request, PolicyEnforcerConfig config) {
    try {
      return PolicyEnforcer.builder()
          .authServerUrl(config.getAuthServerUrl())
          .realm(config.getRealm())
          .clientId(config.getResource())
          .credentials(config.getCredentials())
          .bearerOnly(false)
          .httpClient(httpClient)
          .enforcerConfig(config)
          .build();
    } catch (RuntimeException e) {
      throw new EnforcerUnavailableException(e);
    }
  }

  private boolean isEnforceable(HttpServletRequest request) {
    return request.getDispatcherType() == DispatcherType.REQUEST
        && trust.isAuthenticated(
            SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication());
  }

  /** Building the enforcer failed, which means Keycloak could not be reached or rejected us. */
  private static final class EnforcerUnavailableException extends RuntimeException {

    private EnforcerUnavailableException(RuntimeException cause) {
      super(cause);
    }
  }
}
