package io.codehunters.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class InboundTraceMethodInterceptorTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String PRODUCER_SPAN = "00f067aa0ba902b7";

  private final InboundTraceMethodInterceptor interceptor = new InboundTraceMethodInterceptor();
  private final MessageProperties properties = new MessageProperties();
  private final Map<String, String> seen = new HashMap<>();

  @BeforeEach
  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void should_continue_trace_from_traceparent_with_own_span() throws Throwable {
    properties.setHeader(TraceHeaders.TRACEPARENT, "00-" + TRACE_ID + "-" + PRODUCER_SPAN + "-01");

    invoke();

    assertThat(seen.get(TraceHeaders.MDC_TRACE_ID)).isEqualTo(TRACE_ID);
    assertThat(seen.get(TraceHeaders.MDC_SPAN_ID)).isNotEqualTo(PRODUCER_SPAN).hasSize(16);
  }

  @Test
  void should_use_legacy_x_traceparent_when_traceparent_absent() throws Throwable {
    properties.setHeader(
        TraceHeaders.LEGACY_TRACEPARENT, "00-" + TRACE_ID + "-" + PRODUCER_SPAN + "-01");

    invoke();

    assertThat(seen.get(TraceHeaders.MDC_TRACE_ID)).isEqualTo(TRACE_ID);
  }

  @Test
  void should_use_trace_id_header_from_0x_producers() throws Throwable {
    properties.setHeader(TraceHeaders.TRACE_ID, TRACE_ID);

    invoke();

    assertThat(seen.get(TraceHeaders.MDC_TRACE_ID)).isEqualTo(TRACE_ID);
  }

  @Test
  void should_ignore_invalid_trace_headers() throws Throwable {
    properties.setHeader(TraceHeaders.TRACE_ID, "tr-1");

    invoke();

    assertThat(seen.get(TraceHeaders.MDC_TRACE_ID)).isNull();
  }

  @Test
  void should_clear_mdc_after_listener_returns() throws Throwable {
    properties.setHeader(TraceHeaders.TRACEPARENT, "00-" + TRACE_ID + "-" + PRODUCER_SPAN + "-01");

    invoke();

    assertThat(MDC.get(TraceHeaders.MDC_TRACE_ID)).isNull();
    assertThat(MDC.get(TraceHeaders.MDC_SPAN_ID)).isNull();
  }

  @Test
  void should_keep_existing_context() throws Throwable {
    MDC.put(TraceHeaders.MDC_TRACE_ID, "a".repeat(32));
    properties.setHeader(TraceHeaders.TRACEPARENT, "00-" + TRACE_ID + "-" + PRODUCER_SPAN + "-01");

    invoke();

    assertThat(seen.get(TraceHeaders.MDC_TRACE_ID)).isEqualTo("a".repeat(32));
    assertThat(MDC.get(TraceHeaders.MDC_TRACE_ID)).isEqualTo("a".repeat(32));
  }

  @Test
  void should_continue_trace_for_batch_listeners() throws Throwable {
    properties.setHeader(TraceHeaders.TRACEPARENT, "00-" + TRACE_ID + "-" + PRODUCER_SPAN + "-01");

    invokeWith(java.util.List.of(new Message(new byte[0], properties)));

    assertThat(seen.get(TraceHeaders.MDC_TRACE_ID)).isEqualTo(TRACE_ID);
  }

  private void invoke() throws Throwable {
    invokeWith(new Message(new byte[0], properties));
  }

  private void invokeWith(Object listenerData) throws Throwable {
    MethodInvocation invocation = mock(MethodInvocation.class);
    when(invocation.getArguments()).thenReturn(new Object[] {listenerData});
    when(invocation.proceed())
        .thenAnswer(
            call -> {
              seen.put(TraceHeaders.MDC_TRACE_ID, MDC.get(TraceHeaders.MDC_TRACE_ID));
              seen.put(TraceHeaders.MDC_SPAN_ID, MDC.get(TraceHeaders.MDC_SPAN_ID));
              return null;
            });
    interceptor.invoke(invocation);
  }
}
