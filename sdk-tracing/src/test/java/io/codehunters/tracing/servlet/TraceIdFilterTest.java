package io.codehunters.tracing.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.tracing.TraceHeaders;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String CALLER_SPAN = "00f067aa0ba902b7";

  private final TraceIdFilter filter = new TraceIdFilter();
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api");
  private final Map<String, String> seen = new HashMap<>();
  private final MockFilterChain chain =
      new MockFilterChain() {
        @Override
        public void doFilter(
            jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
          seen.put(TraceHeaders.MDC_TRACE_ID, MDC.get(TraceHeaders.MDC_TRACE_ID));
          seen.put(TraceHeaders.MDC_SPAN_ID, MDC.get(TraceHeaders.MDC_SPAN_ID));
          seen.put(TraceHeaders.MDC_TRACE_FLAGS, MDC.get(TraceHeaders.MDC_TRACE_FLAGS));
        }
      };

  @BeforeEach
  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void should_continue_w3c_trace_with_own_span_when_traceparent_present()
      throws ServletException, IOException {
    request.addHeader(TraceHeaders.TRACEPARENT, "00-" + TRACE_ID + "-" + CALLER_SPAN + "-00");

    run();

    assertThat(seen.get(TraceHeaders.MDC_TRACE_ID)).isEqualTo(TRACE_ID);
    assertThat(seen.get(TraceHeaders.MDC_SPAN_ID)).isNotEqualTo(CALLER_SPAN).hasSize(16);
    assertThat(seen.get(TraceHeaders.MDC_TRACE_FLAGS)).isEqualTo("00");
  }

  @Test
  void should_use_legacy_x_traceparent_when_traceparent_absent()
      throws ServletException, IOException {
    request.addHeader(
        TraceHeaders.LEGACY_TRACEPARENT, "00-" + TRACE_ID + "-" + CALLER_SPAN + "-01");

    run();

    assertThat(seen.get(TraceHeaders.MDC_TRACE_ID)).isEqualTo(TRACE_ID);
  }

  @Test
  void should_prefer_traceparent_over_legacy_header() throws ServletException, IOException {
    request.addHeader(TraceHeaders.TRACEPARENT, "00-" + TRACE_ID + "-" + CALLER_SPAN + "-01");
    request.addHeader(
        TraceHeaders.LEGACY_TRACEPARENT, "00-" + "a".repeat(32) + "-" + CALLER_SPAN + "-01");

    run();

    assertThat(seen.get(TraceHeaders.MDC_TRACE_ID)).isEqualTo(TRACE_ID);
  }

  @Test
  void should_use_trace_id_header_when_no_traceparent() throws ServletException, IOException {
    request.addHeader(TraceHeaders.TRACE_ID, TRACE_ID);

    run();

    assertThat(seen.get(TraceHeaders.MDC_TRACE_ID)).isEqualTo(TRACE_ID);
    assertThat(seen.get(TraceHeaders.MDC_TRACE_FLAGS)).isEqualTo("01");
  }

  @Test
  void should_generate_new_trace_when_header_has_all_zero_trace_id()
      throws ServletException, IOException {
    request.addHeader(TraceHeaders.TRACEPARENT, "00-" + "0".repeat(32) + "-" + CALLER_SPAN + "-01");

    run();

    assertThat(seen.get(TraceHeaders.MDC_TRACE_ID))
        .matches("[0-9a-f]{32}")
        .isNotEqualTo("0".repeat(32));
  }

  @Test
  void should_clear_mdc_when_request_completes() throws ServletException, IOException {
    run();

    assertThat(MDC.get(TraceHeaders.MDC_TRACE_ID)).isNull();
    assertThat(MDC.get(TraceHeaders.MDC_SPAN_ID)).isNull();
    assertThat(MDC.get(TraceHeaders.MDC_TRACE_FLAGS)).isNull();
  }

  @Test
  void should_keep_context_set_by_upstream_instrumentation() throws ServletException, IOException {
    MDC.put(TraceHeaders.MDC_TRACE_ID, TRACE_ID);
    MDC.put(TraceHeaders.MDC_SPAN_ID, CALLER_SPAN);
    request.addHeader(TraceHeaders.TRACEPARENT, "00-" + "a".repeat(32) + "-" + CALLER_SPAN + "-01");

    run();

    assertThat(seen.get(TraceHeaders.MDC_TRACE_ID)).isEqualTo(TRACE_ID);
    assertThat(MDC.get(TraceHeaders.MDC_TRACE_ID)).isEqualTo(TRACE_ID);
  }

  @Test
  void should_reuse_request_trace_on_error_dispatch() throws ServletException, IOException {
    request.addHeader(TraceHeaders.TRACEPARENT, "00-" + TRACE_ID + "-" + CALLER_SPAN + "-01");
    run();
    String requestSpan = seen.get(TraceHeaders.MDC_SPAN_ID);
    seen.clear();
    request.setDispatcherType(DispatcherType.ERROR);

    run();

    assertThat(seen.get(TraceHeaders.MDC_TRACE_ID)).isEqualTo(TRACE_ID);
    assertThat(seen.get(TraceHeaders.MDC_SPAN_ID)).isEqualTo(requestSpan);
  }

  @Test
  void should_reuse_request_trace_on_async_dispatch() throws ServletException, IOException {
    request.addHeader(TraceHeaders.TRACEPARENT, "00-" + TRACE_ID + "-" + CALLER_SPAN + "-01");
    run();
    seen.clear();
    request.setDispatcherType(DispatcherType.ASYNC);

    run();

    assertThat(seen.get(TraceHeaders.MDC_TRACE_ID)).isEqualTo(TRACE_ID);
  }

  @Test
  void should_clear_mdc_when_chain_throws() {
    var failing =
        new MockFilterChain() {
          @Override
          public void doFilter(
              jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
            throw new IllegalStateException("boom");
          }
        };

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> filter.doFilter(request, new MockHttpServletResponse(), failing))
        .isInstanceOf(IllegalStateException.class);

    assertThat(MDC.get(TraceHeaders.MDC_TRACE_ID)).isNull();
  }

  private void run() throws ServletException, IOException {
    filter.doFilter(request, new MockHttpServletResponse(), chain);
  }
}
