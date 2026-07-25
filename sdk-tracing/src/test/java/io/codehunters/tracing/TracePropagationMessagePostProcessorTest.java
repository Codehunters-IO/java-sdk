package io.codehunters.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

class TracePropagationMessagePostProcessorTest {

  private static final String MDC_TRACE_ID = "traceId";
  private static final String MDC_SPAN_ID = "spanId";

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private TracePropagationMessagePostProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new TracePropagationMessagePostProcessor(jsonMapper);
    MDC.clear();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  private Message message(String body) {
    return new Message(body == null ? new byte[0] : body.getBytes(), new MessageProperties());
  }

  @Test
  @DisplayName("should_propagate_mdc_traceId_when_present")
  void should_propagate_mdc_traceId() {
    MDC.put(MDC_TRACE_ID, "11111111111111111111111111111111");
    MDC.put(MDC_SPAN_ID, "2222222222222222");
    Message msg = message("{}");

    Message out = processor.postProcessMessage(msg);

    assertThat((String) out.getMessageProperties().getHeader("x-trace-id"))
        .isEqualTo("11111111111111111111111111111111");
    assertThat((String) out.getMessageProperties().getHeader("x-span-id"))
        .isEqualTo("2222222222222222");
    assertThat((String) out.getMessageProperties().getHeader("x-traceparent"))
        .isEqualTo("00-11111111111111111111111111111111-2222222222222222-01");
  }

  @Test
  @DisplayName("should_extract_traceId_from_body_when_mdc_empty")
  void should_extract_traceId_from_body() {
    Message msg = message("{\"header\":{\"traceId\":\"body-trace-id\"}}");

    Message out = processor.postProcessMessage(msg);

    assertThat((String) out.getMessageProperties().getHeader("x-trace-id"))
        .isEqualTo("body-trace-id");
    assertThat((String) out.getMessageProperties().getHeader("x-span-id")).hasSize(16);
  }

  @Test
  @DisplayName("should_generate_traceId_when_mdc_and_body_missing")
  void should_generate_traceId_when_nothing_known() {
    Message msg = message("{}");

    Message out = processor.postProcessMessage(msg);

    assertThat((String) out.getMessageProperties().getHeader("x-trace-id")).hasSize(32);
    assertThat((String) out.getMessageProperties().getHeader("x-span-id")).hasSize(16);
  }

  @Test
  @DisplayName("should_generate_traceId_when_body_parse_fails")
  void should_generate_traceId_when_body_invalid_json() {
    Message msg = message("not-json");

    Message out = processor.postProcessMessage(msg);

    assertThat((String) out.getMessageProperties().getHeader("x-trace-id")).hasSize(32);
  }

  @Test
  @DisplayName("should_generate_traceId_when_body_has_null_traceId")
  void should_generate_when_body_traceId_null() {
    Message msg = message("{\"header\":{\"traceId\":null}}");

    Message out = processor.postProcessMessage(msg);

    assertThat((String) out.getMessageProperties().getHeader("x-trace-id")).hasSize(32);
  }
}
