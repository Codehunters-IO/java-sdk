package io.codehunters.security.authz.servlet;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.http.client.HttpClient;
import org.keycloak.AuthorizationContext;
import org.keycloak.adapters.authorization.PolicyEnforcer;
import org.keycloak.adapters.authorization.integration.elytron.ServletHttpRequest;
import org.keycloak.adapters.authorization.integration.elytron.ServletHttpResponse;
import org.keycloak.representations.adapters.config.PolicyEnforcerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/**
 * Keycloak's official {@link PolicyEnforcer}, run inside the Spring Security filter chain after
 * authorization, the way Keycloak's {@code ServletPolicyEnforcerFilter} runs it.
 *
 * <p>Only requests a bearer token authenticated are enforced: the decision comes from the
 * authentication Spring Security established, not from request headers. Requests left
 * unauthenticated (public and excluded paths), requests another chain authenticated otherwise
 * (basic auth on actuator, for example) and error dispatches pass through. The token given to
 * Keycloak is the one the authentication holds; when the authentication no longer holds it, the
 * {@link Options#tokenResolver() token resolver} reads it from the request, and a request whose
 * token cannot be found is denied (403).
 *
 * <p>Keycloak matches its path patterns against the decoded request path, without path parameters
 * ({@code ;name=value}), as Spring MVC does when it routes the request.
 *
 * <p>The enforcer is built on the first enforced request; requests arriving meanwhile wait for it.
 * When the build fails (Keycloak unreachable or rejecting the client), requests answer 503 for
 * {@link Options#retryBackoff()} without trying again; afterwards one request rebuilds while the
 * others keep answering 503. A failure of the built enforcer also answers 503. Every failure is
 * logged and reported to {@link Options#failureListener()}.
 */
public class KeycloakPolicyEnforcerFilter implements Filter {

  private static final Logger log = LoggerFactory.getLogger(KeycloakPolicyEnforcerFilter.class);

  private final PolicyEnforcerConfig config;
  private final HttpClient httpClient;
  private final Options options;
  private final AuthenticationTrustResolver trust = new AuthenticationTrustResolverImpl();
  private final Object firstBuild = new Object();
  private final AtomicReference<Instant> retryAt = new AtomicReference<>();
  private volatile PolicyEnforcer enforcer;

  /**
   * Creates the filter with {@link Options#defaults() default options}.
   *
   * @param config the enforcer configuration, shared by every request
   * @param httpClient client for calls to Keycloak; {@code null} uses Keycloak's default, which has
   *     no timeouts
   */
  public KeycloakPolicyEnforcerFilter(PolicyEnforcerConfig config, HttpClient httpClient) {
    this(config, httpClient, Options.defaults());
  }

  /**
   * Creates the filter.
   *
   * @param config the enforcer configuration, shared by every request
   * @param httpClient client for calls to Keycloak; {@code null} uses Keycloak's default, which has
   *     no timeouts
   * @param options how tokens are found and failures handled
   */
  public KeycloakPolicyEnforcerFilter(
      PolicyEnforcerConfig config, HttpClient httpClient, Options options) {
    this.config = Objects.requireNonNull(config, "config");
    this.httpClient = httpClient;
    this.options = Objects.requireNonNull(options, "options");
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    var httpRequest = (HttpServletRequest) request;
    Authentication authentication =
        SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
    if (!isEnforceable(httpRequest, authentication)
        || isGranted(httpRequest, (HttpServletResponse) response, authentication)) {
      chain.doFilter(request, response);
    }
  }

  /**
   * Builds Keycloak's enforcer. Called on the first enforced request, and again after a failed
   * build once the retry backoff elapsed.
   *
   * @param request the request that triggered the build
   * @param config the enforcer configuration
   * @return the enforcer
   */
  protected PolicyEnforcer createPolicyEnforcer(
      HttpServletRequest request, PolicyEnforcerConfig config) {
    return PolicyEnforcer.builder()
        .authServerUrl(config.getAuthServerUrl())
        .realm(config.getRealm())
        .clientId(config.getResource())
        .credentials(config.getCredentials())
        .bearerOnly(false)
        .httpClient(httpClient)
        .enforcerConfig(config)
        .build();
  }

  private boolean isEnforceable(HttpServletRequest request, Authentication authentication) {
    return request.getDispatcherType() == DispatcherType.REQUEST
        && trust.isAuthenticated(authentication)
        && BearerTokens.isTokenBased(authentication);
  }

  private boolean isGranted(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    Optional<String> token = BearerTokens.of(authentication, request, options.tokenResolver());
    if (token.isEmpty()) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "No bearer token to authorize");
      return false;
    }
    return isGranted(request, response, token.get());
  }

  private boolean isGranted(HttpServletRequest request, HttpServletResponse response, String token)
      throws IOException {
    try {
      return enforce(request, response, token);
    } catch (EnforcerUnavailableException e) {
      return unavailable(response);
    } catch (RuntimeException e) {
      log.warn("Keycloak policy enforcement failed: {}", e.toString());
      options.failureListener().accept(e);
      return unavailable(response);
    }
  }

  private boolean enforce(HttpServletRequest request, HttpServletResponse response, String token) {
    PolicyEnforcer current = enforcer(request);
    var view = new ServletHttpRequest(new EnforcedRequest(request, token), () -> token);
    AuthorizationContext context = current.enforce(view, new ServletHttpResponse(response));
    request.setAttribute(AuthorizationContext.class.getName(), context);
    return context.isGranted();
  }

  private PolicyEnforcer enforcer(HttpServletRequest request) {
    PolicyEnforcer current = enforcer;
    if (current != null) {
      return current;
    }
    Instant failedUntil = retryAt.get();
    return failedUntil == null ? buildFirst(request) : rebuild(request, failedUntil);
  }

  // Requests arriving while the first build runs wait for it, as with Keycloak's own filter.
  private PolicyEnforcer buildFirst(HttpServletRequest request) {
    synchronized (firstBuild) {
      PolicyEnforcer current = enforcer;
      if (current != null) {
        return current;
      }
      Instant failedUntil = retryAt.get();
      return failedUntil == null ? build(request) : rebuild(request, failedUntil);
    }
  }

  // After a failed build nobody waits: 503 until the backoff elapses, then one request claims the
  // rebuild by moving the deadline, and the others keep answering 503.
  private PolicyEnforcer rebuild(HttpServletRequest request, Instant failedUntil) {
    Instant now = options.clock().instant();
    if (now.isBefore(failedUntil)
        || !retryAt.compareAndSet(failedUntil, now.plus(options.retryBackoff()))) {
      throw new EnforcerUnavailableException();
    }
    return build(request);
  }

  private PolicyEnforcer build(HttpServletRequest request) {
    try {
      PolicyEnforcer built = createPolicyEnforcer(request, config);
      enforcer = built;
      return built;
    } catch (RuntimeException e) {
      retryAt.set(options.clock().instant().plus(options.retryBackoff()));
      log.warn(
          "Keycloak policy enforcer unavailable, retrying in {}: {}",
          options.retryBackoff(),
          e.toString());
      options.failureListener().accept(e);
      throw new EnforcerUnavailableException();
    }
  }

  private static boolean unavailable(HttpServletResponse response) throws IOException {
    if (!response.isCommitted()) {
      response.sendError(
          HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Authorization service unavailable");
    }
    return false;
  }

  /**
   * How the filter finds bearer tokens and handles failures.
   *
   * @param tokenResolver reads the token from the request when the authentication no longer holds
   *     it; the application's {@link BearerTokenResolver} when it defines one
   * @param retryBackoff how long requests answer 503 after a failed build before one tries again
   * @param clock the clock the backoff is measured with
   * @param failureListener told of every failed build and every failure of the built enforcer
   */
  public record Options(
      BearerTokenResolver tokenResolver,
      Duration retryBackoff,
      Clock clock,
      Consumer<? super RuntimeException> failureListener) {

    private static final Duration DEFAULT_RETRY_BACKOFF = Duration.ofSeconds(10);

    /** Requires every option and a positive backoff. */
    public Options {
      Objects.requireNonNull(tokenResolver, "tokenResolver");
      Objects.requireNonNull(clock, "clock");
      Objects.requireNonNull(failureListener, "failureListener");
      if (retryBackoff == null || !retryBackoff.isPositive()) {
        throw new IllegalArgumentException("retryBackoff must be positive: " + retryBackoff);
      }
    }

    /**
     * Spring Security's default token resolver, a 10 second backoff, the system clock and no
     * listener.
     *
     * @return the default options
     */
    public static Options defaults() {
      return new Options(
          new DefaultBearerTokenResolver(), DEFAULT_RETRY_BACKOFF, Clock.systemUTC(), e -> {});
    }

    /**
     * @param resolver the token resolver to use
     * @return these options with that resolver
     */
    public Options withTokenResolver(BearerTokenResolver resolver) {
      return new Options(resolver, retryBackoff, clock, failureListener);
    }

    /**
     * @param backoff the retry backoff to use
     * @return these options with that backoff
     */
    public Options withRetryBackoff(Duration backoff) {
      return new Options(tokenResolver, backoff, clock, failureListener);
    }

    /**
     * @param backoffClock the clock to measure the backoff with
     * @return these options with that clock
     */
    public Options withClock(Clock backoffClock) {
      return new Options(tokenResolver, retryBackoff, backoffClock, failureListener);
    }

    /**
     * @param listener the failure listener to use
     * @return these options with that listener
     */
    public Options withFailureListener(Consumer<? super RuntimeException> listener) {
      return new Options(tokenResolver, retryBackoff, clock, listener);
    }
  }

  /** The enforcer is not built: the build failed, or failed recently and is not retried yet. */
  private static final class EnforcerUnavailableException extends RuntimeException {

    private EnforcerUnavailableException() {
      super(null, null, false, false);
    }
  }
}
