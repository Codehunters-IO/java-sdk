package io.codehunters.web.errors.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTraceIdProviderTest {

  private final MdcTraceIdProvider provider = new MdcTraceIdProvider();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void should_ignore_a_blank_trace_id_in_the_mdc() {
    MDC.put("traceId", " ");

    assertThat(provider.currentTraceId()).isEmpty();
  }

  @Test
  void should_be_empty_when_the_mdc_has_no_trace_id() {
    assertThat(provider.currentTraceId()).isEmpty();
  }
}
