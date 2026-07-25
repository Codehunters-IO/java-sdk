package io.codehunters.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;

class TraceIdFilterTest {

  private static final String VALID_TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
  private static final String VALID_SPAN_ID = "b7ad6b7169203331";
  private static final String VALID_TRACEPARENT =
      "00-" + VALID_TRACE_ID + "-" + VALID_SPAN_ID + "-01";

  private final TraceIdFilter filter = new TraceIdFilter();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void shouldExtractTraceAndSpanFromValidTraceparent() throws ServletException, IOException {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getHeader(TraceIdFilter.HEADER_TRACEPARENT)).thenReturn(VALID_TRACEPARENT);
    runFilterAssertingMdc(request, VALID_TRACE_ID, VALID_SPAN_ID);
  }

  @Test
  void shouldGenerateBothWhenAllHeadersAbsent() throws ServletException, IOException {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    String[] captured = runFilter(request);
    assertTrue(captured[0] != null && captured[0].matches("[0-9a-f]{32}"));
    assertTrue(captured[1] != null && captured[1].matches("[0-9a-f]{16}"));
  }

  @Test
  void shouldNotOverwritePreExistingMdc() throws ServletException, IOException {
    MDC.put(TraceIdFilter.MDC_TRACE_ID, "preexisting-trace");
    MDC.put(TraceIdFilter.MDC_SPAN_ID, "preexisting-span");
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getHeader(TraceIdFilter.HEADER_TRACEPARENT)).thenReturn(VALID_TRACEPARENT);
    String[] captured = runFilter(request);
    assertEquals("preexisting-trace", captured[0]);
    assertEquals("preexisting-span", captured[1]);
  }

  @Test
  void shouldClearMdcAfterFilterChain() throws ServletException, IOException {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getHeader(TraceIdFilter.HEADER_TRACEPARENT)).thenReturn(VALID_TRACEPARENT);
    filter.doFilter(request, Mockito.mock(HttpServletResponse.class), (req, res) -> {});
    assertNull(MDC.get(TraceIdFilter.MDC_TRACE_ID));
    assertNull(MDC.get(TraceIdFilter.MDC_SPAN_ID));
  }

  @Test
  void shouldUseTraceAndSpanHeadersWhenTraceparentAbsent() throws ServletException, IOException {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getHeader(TraceIdFilter.HEADER_TRACE_ID)).thenReturn(VALID_TRACE_ID);
    Mockito.when(request.getHeader(TraceIdFilter.HEADER_SPAN_ID)).thenReturn(VALID_SPAN_ID);
    runFilterAssertingMdc(request, VALID_TRACE_ID, VALID_SPAN_ID);
  }

  @Test
  void shouldGenerateSpanWhenSpanHeaderHasInvalidHex() throws ServletException, IOException {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getHeader(TraceIdFilter.HEADER_TRACE_ID)).thenReturn(VALID_TRACE_ID);
    Mockito.when(request.getHeader(TraceIdFilter.HEADER_SPAN_ID)).thenReturn("zzzzzzzzzzzzzzzz");
    String[] captured = runFilter(request);
    assertEquals(VALID_TRACE_ID, captured[0]);
    assertTrue(captured[1].matches("[0-9a-f]{16}"));
  }

  @Test
  void shouldGenerateWhenTraceparentHasWrongPartCount() throws ServletException, IOException {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getHeader(TraceIdFilter.HEADER_TRACEPARENT)).thenReturn("00-abc");
    String[] captured = runFilter(request);
    assertTrue(captured[0].matches("[0-9a-f]{32}"));
    assertTrue(captured[1].matches("[0-9a-f]{16}"));
  }

  @Test
  void shouldGenerateWhenTraceparentHasInvalidHex() throws ServletException, IOException {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    String invalidTraceparent = "00-" + "z".repeat(32) + "-" + "z".repeat(16) + "-01";
    Mockito.when(request.getHeader(TraceIdFilter.HEADER_TRACEPARENT))
        .thenReturn(invalidTraceparent);
    String[] captured = runFilter(request);
    assertTrue(captured[0].matches("[0-9a-f]{32}"));
  }

  private void runFilterAssertingMdc(
      HttpServletRequest request, String expectedTraceId, String expectedSpanId)
      throws ServletException, IOException {
    String[] captured = runFilter(request);
    assertEquals(expectedTraceId, captured[0]);
    assertEquals(expectedSpanId, captured[1]);
  }

  private String[] runFilter(HttpServletRequest request) throws ServletException, IOException {
    String[] captured = new String[2];
    filter.doFilter(request, Mockito.mock(HttpServletResponse.class), capturingChain(captured));
    return captured;
  }

  private FilterChain capturingChain(String[] captured) {
    return (req, res) -> {
      captured[0] = MDC.get(TraceIdFilter.MDC_TRACE_ID);
      captured[1] = MDC.get(TraceIdFilter.MDC_SPAN_ID);
    };
  }
}
