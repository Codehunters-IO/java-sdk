package io.codehunters.web.device.servlet;

import io.codehunters.web.device.ip.ProxyChain;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;

/** Reads the {@link ProxyChain} of a servlet request; shared so every reader sees the same one. */
final class ServletProxyChains {

  static final String FORWARDED = "Forwarded";
  static final String X_FORWARDED_FOR = "X-Forwarded-For";

  private ServletProxyChains() {}

  static ProxyChain of(HttpServletRequest request) {
    return new ProxyChain(
        request.getRemoteAddr(),
        headerLines(request, FORWARDED),
        headerLines(request, X_FORWARDED_FOR));
  }

  static List<String> headerLines(HttpServletRequest request, String name) {
    var lines = request.getHeaders(name);
    return lines == null ? List.of() : Collections.list(lines);
  }
}
