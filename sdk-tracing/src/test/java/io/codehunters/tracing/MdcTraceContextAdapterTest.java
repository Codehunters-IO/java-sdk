package io.codehunters.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTraceContextAdapterTest {

  private MdcTraceContextAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new MdcTraceContextAdapter();
    MDC.clear();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  @DisplayName("should_return_traceId_from_mdc_when_present")
  void should_return_traceId_when_present() {
    MDC.put(MdcTraceContextAdapter.MDC_TRACE_ID, "abc123");

    assertThat(adapter.currentTraceId()).isEqualTo("abc123");
  }

  @Test
  @DisplayName("should_return_null_traceId_when_mdc_empty")
  void should_return_null_traceId_when_empty() {
    assertThat(adapter.currentTraceId()).isNull();
  }

  @Test
  @DisplayName("should_return_spanId_from_mdc_when_present")
  void should_return_spanId_when_present() {
    MDC.put(MdcTraceContextAdapter.MDC_SPAN_ID, "span-42");

    assertThat(adapter.currentSpanId()).isEqualTo("span-42");
  }

  @Test
  @DisplayName("should_return_null_spanId_when_mdc_empty")
  void should_return_null_spanId_when_empty() {
    assertThat(adapter.currentSpanId()).isNull();
  }
}
