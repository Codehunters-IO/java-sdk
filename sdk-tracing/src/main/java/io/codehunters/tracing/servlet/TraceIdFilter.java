package io.codehunters.tracing.servlet;

import io.codehunters.tracing.TraceHeaders;
import io.codehunters.tracing.TraceParent;
import io.codehunters.tracing.internal.TraceMdc;
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
 * new span for this service; otherwise starts a trace. The context is kept on the request, so async
 * and error dispatches log the same trace. Leaves the MDC alone when other instrumentation, such as
 * Micrometer's observation filter, already populated it.
 */
public class TraceIdFilter extends OncePerRequestFilter {

  private static final String CONTEXT_ATTRIBUTE = TraceIdFilter.class.getName() + ".context";

  /** Creates the filter. */
  public TraceIdFilter() {}

  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
  }

  @Override
  protected boolean shouldNotFilterErrorDispatch() {
    return false;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (TraceMdc.isActive()) {
      chain.doFilter(request, response);
      return;
    }
    TraceMdc.put(requestContext(request));
    try {
      chain.doFilter(request, response);
    } finally {
      TraceMdc.clear();
    }
  }

  private static TraceParent requestContext(HttpServletRequest request) {
    if (request.getAttribute(CONTEXT_ATTRIBUTE) instanceof TraceParent stored) {
      return stored;
    }
    TraceParent context =
        incoming(request).map(TraceParent::child).orElseGet(TraceParent::generate);
    request.setAttribute(CONTEXT_ATTRIBUTE, context);
    return context;
  }

  private static Optional<TraceParent> incoming(HttpServletRequest request) {
    return TraceParent.parse(request.getHeader(TraceHeaders.TRACEPARENT))
        .or(() -> TraceParent.parse(request.getHeader(TraceHeaders.LEGACY_TRACEPARENT)))
        .or(() -> TraceParent.ofTraceId(request.getHeader(TraceHeaders.TRACE_ID)));
  }
}
