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
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
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
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Keycloak's official {@link PolicyEnforcer}, run inside the Spring Security filter chain after
 * authorization, the way Keycloak's {@code ServletPolicyEnforcerFilter} runs it.
 *
 * <p>Every authenticated request (request, forward and include dispatches) is enforced unless its
 * path is one of {@link Options#skipPaths()}; unauthenticated requests (public and excluded paths)
 * and error dispatches pass through. The decision fails closed:
 *
 * <ul>
 *   <li>Only the token Spring Security validated is given to Keycloak, read from the authentication
 *       ({@code KeycloakAuthenticationToken} or Spring's own token authentications), never from the
 *       request: Keycloak grants from a requesting party token's permissions without checking its
 *       signature.
 *   <li>An authentication without such a token (basic auth, form login, a custom principal) is
 *       denied (403); list the paths such chains serve in {@link Options#skipPaths()}.
 *   <li>A token outside the bearer charset, or a path whose decoding is ambiguous, is denied.
 *   <li>A denial Keycloak did not answer itself answers 403.
 * </ul>
 *
 * <p>Keycloak matches its path patterns against the path as Spring MVC matches it: decoded per
 * segment, without path parameters.
 *
 * <p>The enforcer is built on the first enforced request; requests arriving meanwhile wait up to
 * {@link Options#firstBuildWait()}, then answer 503. When the build fails (Keycloak unreachable or
 * rejecting the client), requests answer 503 for {@link Options#retryBackoff()} without trying
 * again; afterwards one request rebuilds while the others keep answering 503, however long the
 * rebuild takes. A failure of the built enforcer also answers 503. Every failure is logged and
 * reported to {@link Options#failureListener()}.
 */
public class KeycloakPolicyEnforcerFilter implements Filter {

  private static final Logger log = LoggerFactory.getLogger(KeycloakPolicyEnforcerFilter.class);
  private static final Set<DispatcherType> ENFORCED_DISPATCHES =
      EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.INCLUDE);

  private final PolicyEnforcerConfig config;
  private final HttpClient httpClient;
  private final Options options;
  private final AuthenticationTrustResolver trust = new AuthenticationTrustResolverImpl();
  private final ReentrantLock firstBuild = new ReentrantLock();
  private final AtomicBoolean building = new AtomicBoolean();
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
   * @param options which paths are skipped and how failures are handled
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
    return ENFORCED_DISPATCHES.contains(request.getDispatcherType())
        && trust.isAuthenticated(authentication)
        && !options.skipPaths().matches(request);
  }

  private boolean isGranted(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    Optional<String> token = BearerTokens.of(authentication);
    if (token.isEmpty()) {
      return forbid(response, "Only bearer-token authentications are authorized");
    }
    if (!BearerTokens.isBearerCharset(token.get())) {
      return forbid(response, "Malformed bearer token");
    }
    if (EnforcedRequest.isAmbiguous(request)) {
      return forbid(response, "Ambiguous request path");
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

  private boolean enforce(HttpServletRequest request, HttpServletResponse response, String token)
      throws IOException {
    PolicyEnforcer current = enforcer(request);
    var view = new ServletHttpRequest(new EnforcedRequest(request, token), () -> token);
    AuthorizationContext context = current.enforce(view, new ServletHttpResponse(response));
    request.setAttribute(AuthorizationContext.class.getName(), context);
    return context.isGranted() || deniedSilently(response);
  }

  // A denial must answer: an empty 200 would read as success to the client.
  private static boolean deniedSilently(HttpServletResponse response) throws IOException {
    if (!response.isCommitted()) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
    return false;
  }

  private PolicyEnforcer enforcer(HttpServletRequest request) {
    PolicyEnforcer current = enforcer;
    if (current != null) {
      return current;
    }
    Instant failedUntil = retryAt.get();
    return failedUntil == null ? buildFirst(request) : rebuild(request, failedUntil);
  }

  // Requests arriving while the first build runs wait for it a bounded time, then answer 503.
  private PolicyEnforcer buildFirst(HttpServletRequest request) {
    if (!acquireFirstBuild()) {
      throw new EnforcerUnavailableException();
    }
    try {
      PolicyEnforcer current = enforcer;
      if (current != null) {
        return current;
      }
      Instant failedUntil = retryAt.get();
      return failedUntil == null ? build(request) : rebuild(request, failedUntil);
    } finally {
      firstBuild.unlock();
    }
  }

  private boolean acquireFirstBuild() {
    try {
      return firstBuild.tryLock(options.firstBuildWait().toNanos(), TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  // After a failed build nobody waits: 503 until the backoff elapses, then one request rebuilds
  // and the others keep answering 503.
  private PolicyEnforcer rebuild(HttpServletRequest request, Instant failedUntil) {
    if (options.clock().instant().isBefore(failedUntil)) {
      throw new EnforcerUnavailableException();
    }
    return build(request);
  }

  // One build at a time, however long it takes: a slow build is never duplicated.
  private PolicyEnforcer build(HttpServletRequest request) {
    if (!building.compareAndSet(false, true)) {
      throw new EnforcerUnavailableException();
    }
    try {
      return buildOnce(request);
    } finally {
      building.set(false);
    }
  }

  private PolicyEnforcer buildOnce(HttpServletRequest request) {
    PolicyEnforcer current = enforcer;
    if (current != null) {
      return current;
    }
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

  private static boolean forbid(HttpServletResponse response, String reason) throws IOException {
    log.debug("Request forbidden before Keycloak was asked: {}", reason);
    response.sendError(HttpServletResponse.SC_FORBIDDEN);
    return false;
  }

  private static boolean unavailable(HttpServletResponse response) throws IOException {
    if (!response.isCommitted()) {
      response.sendError(
          HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Authorization service unavailable");
    }
    return false;
  }

  /**
   * Which requests the filter leaves alone and how it handles failures.
   *
   * @param skipPaths requests never enforced, whatever authenticated them (actuator endpoints
   *     behind basic auth, for example)
   * @param retryBackoff how long requests answer 503 after a failed build before one tries again
   * @param firstBuildWait how long a request waits for the first build before answering 503
   * @param clock the clock the backoff is measured with
   * @param failureListener told of every failed build and every failure of the built enforcer
   */
  public record Options(
      RequestMatcher skipPaths,
      Duration retryBackoff,
      Duration firstBuildWait,
      Clock clock,
      Consumer<? super RuntimeException> failureListener) {

    private static final RequestMatcher NO_PATHS = request -> false;
    private static final Duration DEFAULT_RETRY_BACKOFF = Duration.ofSeconds(10);
    private static final Duration DEFAULT_FIRST_BUILD_WAIT = Duration.ofSeconds(5);

    /** Requires every option and positive durations. */
    public Options {
      Objects.requireNonNull(skipPaths, "skipPaths");
      Objects.requireNonNull(clock, "clock");
      Objects.requireNonNull(failureListener, "failureListener");
      requirePositive(retryBackoff, "retryBackoff");
      requirePositive(firstBuildWait, "firstBuildWait");
    }

    /**
     * No skipped paths, a 10 second backoff, a 5 second wait for the first build, the system clock
     * and no listener.
     *
     * @return the default options
     */
    public static Options defaults() {
      return new Options(
          NO_PATHS, DEFAULT_RETRY_BACKOFF, DEFAULT_FIRST_BUILD_WAIT, Clock.systemUTC(), e -> {});
    }

    /**
     * @param paths requests never enforced
     * @return these options with those skipped paths
     */
    public Options withSkipPaths(RequestMatcher paths) {
      return new Options(paths, retryBackoff, firstBuildWait, clock, failureListener);
    }

    /**
     * @param backoff the retry backoff to use
     * @return these options with that backoff
     */
    public Options withRetryBackoff(Duration backoff) {
      return new Options(skipPaths, backoff, firstBuildWait, clock, failureListener);
    }

    /**
     * @param wait how long a request waits for the first build
     * @return these options with that wait
     */
    public Options withFirstBuildWait(Duration wait) {
      return new Options(skipPaths, retryBackoff, wait, clock, failureListener);
    }

    /**
     * @param backoffClock the clock to measure the backoff with
     * @return these options with that clock
     */
    public Options withClock(Clock backoffClock) {
      return new Options(skipPaths, retryBackoff, firstBuildWait, backoffClock, failureListener);
    }

    /**
     * @param listener the failure listener to use
     * @return these options with that listener
     */
    public Options withFailureListener(Consumer<? super RuntimeException> listener) {
      return new Options(skipPaths, retryBackoff, firstBuildWait, clock, listener);
    }

    private static void requirePositive(Duration duration, String name) {
      if (duration == null || !duration.isPositive()) {
        throw new IllegalArgumentException(name + " must be positive: " + duration);
      }
    }
  }

  /** The enforcer is not built: the build failed, or failed recently and is not retried yet. */
  private static final class EnforcerUnavailableException extends RuntimeException {

    private EnforcerUnavailableException() {
      super(null, null, false, false);
    }
  }
}
