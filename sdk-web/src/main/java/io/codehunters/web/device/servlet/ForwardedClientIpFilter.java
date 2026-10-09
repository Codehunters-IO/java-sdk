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
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Makes the rest of the request see one client address: {@code getRemoteAddr()} and {@code
 * getRemoteHost()} answer the client that {@link ClientIpResolver} found behind the trusted
 * proxies, and the {@code Forwarded} and {@code X-Forwarded-For} headers are hidden, so a later
 * reader (Spring Security, Spring's {@code ForwardedHeaderFilter}, application code) cannot pick a
 * different, client written one. A request whose remote address is not an IP literal is passed on
 * untouched.
 *
 * <p>It runs inside the servlet container: container access logs still record the proxy. Use {@code
 * server.forward-headers-strategy=native} when they must record the client too.
 */
public final class ForwardedClientIpFilter extends OncePerRequestFilter implements Ordered {

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
   * First, like Spring's {@code ForwardedHeaderFilter}, so every other filter sees the client.
   *
   * @return {@link Ordered#HIGHEST_PRECEDENCE}
   */
  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
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

  private static final class ClientRequest extends HttpServletRequestWrapper {

    private final String client;

    ClientRequest(HttpServletRequest request, String client) {
      super(request);
      this.client = client;
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
      return isForwarding(name) ? null : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      return isForwarding(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      var names = Collections.list(super.getHeaderNames());
      names.removeIf(ClientRequest::isForwarding);
      return Collections.enumeration(names);
    }

    private static boolean isForwarding(String name) {
      return ServletProxyChains.FORWARDED.equalsIgnoreCase(name)
          || ServletProxyChains.X_FORWARDED_FOR.equalsIgnoreCase(name);
    }
  }
}
