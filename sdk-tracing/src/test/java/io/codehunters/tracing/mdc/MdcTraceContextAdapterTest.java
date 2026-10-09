package io.codehunters.tracing.mdc;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.tracing.TraceHeaders;
import io.codehunters.tracing.TraceParent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTraceContextAdapterTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String SPAN_ID = "00f067aa0ba902b7";

  private final MdcTraceContextAdapter adapter = new MdcTraceContextAdapter();

  @BeforeEach
  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void should_be_empty_when_no_trace_is_active() {
    assertThat(adapter.currentTraceId()).isEmpty();
    assertThat(adapter.currentSpanId()).isEmpty();
    assertThat(adapter.currentTraceParent()).isEmpty();
  }

  @Test
  void should_expose_active_trace() {
    MDC.put(TraceHeaders.MDC_TRACE_ID, TRACE_ID);
    MDC.put(TraceHeaders.MDC_SPAN_ID, SPAN_ID);
    MDC.put(TraceHeaders.MDC_TRACE_FLAGS, "00");

    assertThat(adapter.currentTraceId()).contains(TRACE_ID);
    assertThat(adapter.currentSpanId()).contains(SPAN_ID);
    assertThat(adapter.currentTraceParent()).contains(new TraceParent(TRACE_ID, SPAN_ID, false));
  }

  @Test
  void should_treat_missing_flags_as_sampled() {
    MDC.put(TraceHeaders.MDC_TRACE_ID, TRACE_ID);
    MDC.put(TraceHeaders.MDC_SPAN_ID, SPAN_ID);

    assertThat(adapter.currentTraceParent())
        .hasValueSatisfying(parent -> assertThat(parent.sampled()).isTrue());
  }

  @Test
  void should_derive_trace_parent_when_only_trace_id_is_in_mdc() {
    MDC.put(TraceHeaders.MDC_TRACE_ID, TRACE_ID);

    assertThat(adapter.currentTraceParent())
        .hasValueSatisfying(parent -> assertThat(parent.traceId()).isEqualTo(TRACE_ID));
  }

  @Test
  void should_have_no_trace_parent_when_mdc_holds_non_w3c_ids() {
    MDC.put(TraceHeaders.MDC_TRACE_ID, "tr-1");
    MDC.put(TraceHeaders.MDC_SPAN_ID, "sp-1");

    assertThat(adapter.currentTraceId()).contains("tr-1");
    assertThat(adapter.currentTraceParent()).isEmpty();
  }
}
