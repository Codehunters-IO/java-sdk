package io.codehunters.web.device.servlet;

import io.codehunters.web.device.ip.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Makes the rest of the request see one client address: {@code getRemoteAddr()} and {@code
 * getRemoteHost()} answer the client that {@link ClientIpResolver} found behind the trusted
 * proxies, and the headers naming a client are hidden, so a later reader (Spring Security,
 * application code) cannot pick a different, client written one: {@code X-Forwarded-For}, {@code
 * X-Real-IP} and the {@code for} parameters of {@code Forwarded}. The other {@code Forwarded}
 * parameters ({@code proto}, {@code host}, {@code by}) and {@code X-Forwarded-Host}, {@code -Proto}
 * and {@code -Port} pass through. A request whose remote address is not an IP literal is passed on
 * untouched. Error dispatches are filtered too, so the error page sees the client.
 *
 * <p>It must not be combined with Spring's {@code ForwardedHeaderFilter}, which trusts the leftmost
 * forwarded address from any client; the auto-configuration refuses to start when both are
 * registered. It runs inside the servlet container: container access logs still record the proxy.
 * Use {@code server.forward-headers-strategy=native} when they must record the client too.
 */
public final class ForwardedClientIpFilter extends OncePerRequestFilter implements Ordered {

  private static final List<String> CLIENT_HEADERS =
      List.of(ServletProxyChains.X_FORWARDED_FOR, "X-Real-IP");

  private final ClientIpResolver resolver;

  /**
   * Creates the filter.
   *
   * @param resolver resolves the client address behind the trusted proxies
   */
  public ForwardedClientIpFilter(ClientIpResolver resolver) {
    if (resolver == null) {
      throw new IllegalArgumentException("client ip resolver must not be null");
    }
    this.resolver = resolver;
  }

  /**
   * Right after {@link Ordered#HIGHEST_PRECEDENCE}, so every other filter sees the client.
   *
   * @return {@link Ordered#HIGHEST_PRECEDENCE} + 1
   */
  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 1;
  }

  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var client = resolver.resolve(ServletProxyChains.of(request)).map(InetAddress::getHostAddress);
    chain.doFilter(
        client.<HttpServletRequest>map(ip -> new ClientRequest(request, ip)).orElse(request),
        response);
  }

  // An error dispatch nested in the request this filter already wrapped (Boot's ErrorPageFilter)
  // may carry the unwrapped request.
  @Override
  protected void doFilterNestedErrorDispatch(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    doFilterInternal(request, response, chain);
  }

  private static final class ClientRequest extends HttpServletRequestWrapper {

    private final String client;
    private final List<String> forwarded;

    ClientRequest(HttpServletRequest request, String client) {
      super(request);
      this.client = client;
      this.forwarded =
          ForwardedForParameters.strip(
              ServletProxyChains.headerLines(request, ServletProxyChains.FORWARDED));
    }

    @Override
    public String getRemoteAddr() {
      return client;
    }

    @Override
    public String getRemoteHost() {
      return client;
    }

    @Override
    public String getHeader(String name) {
      if (isForwarded(name)) {
        return forwarded.isEmpty() ? null : forwarded.getFirst();
      }
      return isClientHeader(name) ? null : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      if (isForwarded(name)) {
        return Collections.enumeration(forwarded);
      }
      return isClientHeader(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      var names = Collections.list(super.getHeaderNames());
      names.removeIf(name -> isClientHeader(name) || (isForwarded(name) && forwarded.isEmpty()));
      return Collections.enumeration(names);
    }

    private static boolean isForwarded(String name) {
      return ServletProxyChains.FORWARDED.equalsIgnoreCase(name);
    }

    private static boolean isClientHeader(String name) {
      return CLIENT_HEADERS.stream().anyMatch(header -> header.equalsIgnoreCase(name));
    }
  }
}
