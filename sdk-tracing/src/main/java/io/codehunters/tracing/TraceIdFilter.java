package io.codehunters.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the request's trace context in the MDC: continues the caller's trace from {@code
 * traceparent}, then the gateway's {@code x-traceparent}, then a bare {@code x-trace-id}, with a
 * new span for this service; otherwise starts a trace. Leaves the MDC alone when other
 * instrumentation already populated it.
 */
public class TraceIdFilter extends OncePerRequestFilter {

  /** Creates the filter. */
  public TraceIdFilter() {}

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (TraceMdc.isActive()) {
      chain.doFilter(request, response);
      return;
    }
    TraceMdc.put(incoming(request).map(TraceParent::child).orElseGet(TraceParent::generate));
    try {
      chain.doFilter(request, response);
    } finally {
      TraceMdc.clear();
    }
  }

  private static Optional<TraceParent> incoming(HttpServletRequest request) {
    return TraceParent.parse(request.getHeader(TraceHeaders.TRACEPARENT))
        .or(() -> TraceParent.parse(request.getHeader(TraceHeaders.LEGACY_TRACEPARENT)))
        .or(() -> TraceParent.ofTraceId(request.getHeader(TraceHeaders.TRACE_ID)));
  }
}
