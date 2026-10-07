package io.codehunters.device.servlet;

import io.codehunters.device.ClientContext;
import io.codehunters.device.ip.ClientIpResolver;
import io.codehunters.device.ip.ProxyChain;
import io.codehunters.device.useragent.UserAgentInfo;
import io.codehunters.device.useragent.UserAgentParser;
import io.codehunters.device.useragent.UserAgents;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the {@link ClientContext} of a servlet request. The result is kept as a request
 * attribute, so filters, controllers and the audit trail of one request share a single resolution.
 */
public final class ClientContextResolver {

  /** Request attribute holding the resolved context. */
  public static final String ATTRIBUTE = ClientContextResolver.class.getName() + ".CONTEXT";

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
    if (RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes) {
      return Optional.of(resolve(attributes.getRequest()));
    }
    return Optional.empty();
  }

  private ClientContext compute(HttpServletRequest request) {
    var chain =
        new ProxyChain(
            request.getRemoteAddr(),
            headerLines(request, "Forwarded"),
            headerLines(request, "X-Forwarded-For"));
    var rawUserAgent = UserAgents.sanitize(request.getHeader("User-Agent"), maxUserAgentLength);
    var userAgent =
        rawUserAgent.isEmpty() ? UserAgentInfo.UNKNOWN : userAgentParser.parse(rawUserAgent);
    return new ClientContext(ipResolver.resolve(chain), userAgent, rawUserAgent);
  }

  private static List<String> headerLines(HttpServletRequest request, String name) {
    var lines = request.getHeaders(name);
    return lines == null ? List.of() : Collections.list(lines);
  }
}
