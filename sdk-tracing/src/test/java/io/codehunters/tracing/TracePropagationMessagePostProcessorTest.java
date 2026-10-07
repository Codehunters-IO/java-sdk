package io.codehunters.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class TracePropagationMessagePostProcessorTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String SPAN_ID = "00f067aa0ba902b7";

  private final TracePropagationMessagePostProcessor processor =
      new TracePropagationMessagePostProcessor();

  @BeforeEach
  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void should_write_w3c_and_legacy_headers_from_mdc() {
    MDC.put(TraceHeaders.MDC_TRACE_ID, TRACE_ID);
    MDC.put(TraceHeaders.MDC_SPAN_ID, SPAN_ID);
    MDC.put(TraceHeaders.MDC_TRACE_FLAGS, "00");

    MessageProperties headers = process(message("{}")).getMessageProperties();

    String expected = "00-" + TRACE_ID + "-" + SPAN_ID + "-00";
    assertThat((String) headers.getHeader(TraceHeaders.TRACEPARENT)).isEqualTo(expected);
    assertThat((String) headers.getHeader(TraceHeaders.LEGACY_TRACEPARENT)).isEqualTo(expected);
    assertThat((String) headers.getHeader(TraceHeaders.TRACE_ID)).isEqualTo(TRACE_ID);
    assertThat((String) headers.getHeader(TraceHeaders.SPAN_ID)).isEqualTo(SPAN_ID);
  }

  @Test
  void should_keep_traceparent_already_on_message() {
    MDC.put(TraceHeaders.MDC_TRACE_ID, "a".repeat(32));
    MDC.put(TraceHeaders.MDC_SPAN_ID, SPAN_ID);
    Message message = message("{}");
    String existing = "00-" + TRACE_ID + "-" + SPAN_ID + "-01";
    message.getMessageProperties().setHeader(TraceHeaders.TRACEPARENT, existing);

    MessageProperties headers = process(message).getMessageProperties();

    assertThat((String) headers.getHeader(TraceHeaders.TRACEPARENT)).isEqualTo(existing);
  }

  @Test
  void should_use_trace_id_from_event_envelope_when_mdc_empty() {
    MessageProperties headers =
        process(message("{\"header\":{\"traceId\":\"" + TRACE_ID + "\"}}")).getMessageProperties();

    assertThat((String) headers.getHeader(TraceHeaders.TRACE_ID)).isEqualTo(TRACE_ID);
  }

  @Test
  void should_generate_trace_when_envelope_trace_id_is_not_w3c() {
    MessageProperties headers =
        process(message("{\"header\":{\"traceId\":\"4bf92f35-77b3-4da6-a3ce-929d0e0e4736\"}}"))
            .getMessageProperties();

    assertThat((String) headers.getHeader(TraceHeaders.TRACE_ID)).matches("[0-9a-f]{32}");
  }

  @Test
  void should_generate_trace_when_body_is_not_json() {
    MessageProperties headers = process(message("not json")).getMessageProperties();

    assertThat(TraceParent.parse(headers.getHeader(TraceHeaders.TRACEPARENT))).isPresent();
  }

  @Test
  void should_keep_mdc_trace_id_when_span_id_is_missing() {
    MDC.put(TraceHeaders.MDC_TRACE_ID, TRACE_ID);

    MessageProperties headers = process(message("{}")).getMessageProperties();

    assertThat((String) headers.getHeader(TraceHeaders.TRACE_ID)).isEqualTo(TRACE_ID);
  }

  @Test
  void should_keep_traceparent_sent_with_different_case_or_as_bytes() {
    Message message = message("{}");
    String existing = "00-" + TRACE_ID + "-" + SPAN_ID + "-01";
    message
        .getMessageProperties()
        .setHeader("Traceparent", existing.getBytes(StandardCharsets.UTF_8));

    MessageProperties headers = process(message).getMessageProperties();

    assertThat((String) headers.getHeader(TraceHeaders.TRACEPARENT)).isEqualTo(existing);
  }

  private Message process(Message message) {
    return processor.postProcessMessage(message);
  }

  private static Message message(String body) {
    return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
  }
}
