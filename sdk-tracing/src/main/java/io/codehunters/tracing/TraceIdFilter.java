package io.codehunters.tracing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that establishes a trace/span context in the {@link MDC} for each request, derived
 * from a W3C {@code traceparent} header, explicit trace/span headers, or freshly generated IDs.
 * Defers to upstream OTEL instrumentation when the MDC is already populated.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

  /** Request header carrying the W3C traceparent. */
  public static final String HEADER_TRACEPARENT = "x-traceparent";

  /** Request header carrying the trace ID. */
  public static final String HEADER_TRACE_ID = "x-trace-id";

  /** Request header carrying the span ID. */
  public static final String HEADER_SPAN_ID = "x-span-id";

  /** MDC key holding the trace ID. */
  public static final String MDC_TRACE_ID = "traceId";

  /** MDC key holding the span ID. */
  public static final String MDC_SPAN_ID = "spanId";

  private static final int TRACE_ID_HEX_LENGTH = 32;
  private static final int SPAN_ID_HEX_LENGTH = 16;
  private static final int TRACEPARENT_PARTS = 4;

  private final SecureRandom random = new SecureRandom();

  /** Creates the filter. */
  public TraceIdFilter() {}

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // OTEL coexistence: if MDC already populated by upstream instrumentation, do not overwrite.
    boolean preExisting = MDC.get(MDC_TRACE_ID) != null;
    String previousTrace = MDC.get(MDC_TRACE_ID);
    String previousSpan = MDC.get(MDC_SPAN_ID);

    if (!preExisting) {
      TraceContext context = resolveContext(request);
      MDC.put(MDC_TRACE_ID, context.traceId());
      MDC.put(MDC_SPAN_ID, context.spanId());
    }
    try {
      filterChain.doFilter(request, response);
    } finally {
      if (!preExisting) {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
      } else {
        if (previousTrace != null) MDC.put(MDC_TRACE_ID, previousTrace);
        if (previousSpan != null) MDC.put(MDC_SPAN_ID, previousSpan);
      }
    }
  }

  private TraceContext resolveContext(HttpServletRequest request) {
    TraceContext fromTraceparent = parseTraceparent(request.getHeader(HEADER_TRACEPARENT));
    if (fromTraceparent != null) {
      return fromTraceparent;
    }
    String traceId = request.getHeader(HEADER_TRACE_ID);
    String spanId = request.getHeader(HEADER_SPAN_ID);
    if (isValidHex(traceId, TRACE_ID_HEX_LENGTH)) {
      return new TraceContext(
          traceId,
          isValidHex(spanId, SPAN_ID_HEX_LENGTH) ? spanId : generateHex(SPAN_ID_HEX_LENGTH));
    }
    return new TraceContext(generateHex(TRACE_ID_HEX_LENGTH), generateHex(SPAN_ID_HEX_LENGTH));
  }

  private TraceContext parseTraceparent(String traceparent) {
    if (traceparent == null || traceparent.isBlank()) {
      return null;
    }
    String[] parts = traceparent.split("-");
    if (parts.length != TRACEPARENT_PARTS) {
      return null;
    }
    String traceId = parts[1];
    String spanId = parts[2];
    if (!isValidHex(traceId, TRACE_ID_HEX_LENGTH) || !isValidHex(spanId, SPAN_ID_HEX_LENGTH)) {
      return null;
    }
    return new TraceContext(traceId, spanId);
  }

  private boolean isValidHex(String value, int length) {
    if (value == null || value.length() != length) {
      return false;
    }
    for (int i = 0; i < length; i++) {
      char c = value.charAt(i);
      boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
      if (!hex) {
        return false;
      }
    }
    return true;
  }

  private String generateHex(int hexLength) {
    byte[] bytes = new byte[hexLength / 2];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private record TraceContext(String traceId, String spanId) {}
}
