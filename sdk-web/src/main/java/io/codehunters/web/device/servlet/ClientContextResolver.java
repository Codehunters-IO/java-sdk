package io.codehunters.web.device.servlet;

import io.codehunters.web.device.ClientContext;
import io.codehunters.web.device.ip.ClientIpResolver;
import io.codehunters.web.device.useragent.UserAgentInfo;
import io.codehunters.web.device.useragent.UserAgentParser;
import io.codehunters.web.device.useragent.UserAgents;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.System.Logger.Level;
import java.util.Optional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the {@link ClientContext} of a servlet request. The result is kept as a request
 * attribute, so filters, controllers and the audit trail of one request share a single resolution.
 */
public final class ClientContextResolver {

  /** Request attribute holding the resolved context. */
  public static final String ATTRIBUTE = ClientContextResolver.class.getName() + ".CONTEXT";

  private static final System.Logger LOG = System.getLogger(ClientContextResolver.class.getName());

  private final ClientIpResolver ipResolver;
  private final UserAgentParser userAgentParser;
  private final int maxUserAgentLength;

  /**
   * Creates the resolver.
   *
   * @param ipResolver resolves the client address behind trusted proxies
   * @param userAgentParser parses the sanitized {@code User-Agent}
   * @param maxUserAgentLength the {@code User-Agent} length kept and parsed, at least 1
   */
  public ClientContextResolver(
      ClientIpResolver ipResolver, UserAgentParser userAgentParser, int maxUserAgentLength) {
    if (ipResolver == null || userAgentParser == null) {
      throw new IllegalArgumentException("ip resolver and user agent parser must not be null");
    }
    if (maxUserAgentLength < 1) {
      throw new IllegalArgumentException("max user agent length must be at least 1");
    }
    this.ipResolver = ipResolver;
    this.userAgentParser = userAgentParser;
    this.maxUserAgentLength = maxUserAgentLength;
  }

  /**
   * Resolves the context of a request, once per request.
   *
   * @param request the request
   * @return the client context
   */
  public ClientContext resolve(HttpServletRequest request) {
    if (request.getAttribute(ATTRIBUTE) instanceof ClientContext cached) {
      return cached;
    }
    var context = compute(request);
    request.setAttribute(ATTRIBUTE, context);
    return context;
  }

  /**
   * Resolves the context of the request bound to the current thread.
   *
   * @return the client context, empty outside a servlet request
   */
  public Optional<ClientContext> current() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
        && isActive(attributes)) {
      return Optional.of(resolve(attributes.getRequest()));
    }
    return Optional.empty();
  }

  // With inherited request attributes a child thread can outlive the request, whose recycled
  // object may already serve another client. Spring refuses request-scope reads once it completed.
  private static boolean isActive(ServletRequestAttributes attributes) {
    try {
      attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
      return true;
    } catch (IllegalStateException e) {
      return false;
    }
  }

  private ClientContext compute(HttpServletRequest request) {
    var chain = ServletProxyChains.of(request);
    var rawUserAgent = UserAgents.sanitize(request.getHeader("User-Agent"), maxUserAgentLength);
    var userAgent = rawUserAgent.isEmpty() ? UserAgentInfo.UNKNOWN : parse(rawUserAgent);
    return new ClientContext(ipResolver.resolve(chain), userAgent, rawUserAgent);
  }

  // Any parser, including one the application defines, answers UNKNOWN rather than failing the
  // request; the cached uap parser already does so on its own.
  private UserAgentInfo parse(String rawUserAgent) {
    try {
      return Optional.ofNullable(userAgentParser.parse(rawUserAgent)).orElse(UserAgentInfo.UNKNOWN);
    } catch (RuntimeException e) {
      LOG.log(Level.WARNING, "User-Agent parser failed: {0}", e.getClass().getName());
      return UserAgentInfo.UNKNOWN;
    }
  }
}
