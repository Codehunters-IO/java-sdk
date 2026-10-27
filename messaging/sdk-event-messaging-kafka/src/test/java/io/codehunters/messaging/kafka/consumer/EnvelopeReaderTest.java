package io.codehunters.messaging.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.codehunters.messaging.MessagePublisherPort;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class EnvelopeReaderTest {

  private static final String TOPIC = "orders";
  private static final String ATTEMPTS_HEADER = "retry_topic-attempts";
  private static final String HEADER_9_2 =
      """
      "header":{"traceId":"t-1","version":"1.0.0","schemaVersion":"2.0.0","origin":"app",\
      "timestamp":"2026-10-27T10:00:00Z","locale":"es-MX"\
      """;
  private static final String BODY =
      """
      "body":{"aggregate":"a-1","eventName":"OrderPlaced","eventType":"EVENT",\
      "payload":{"name":"widget"}}\
      """;
  private static final String ENVELOPE_9_3 =
      "{" + HEADER_9_2 + ",\"eventId\":\"evt-1\",\"idempotencyKey\":\"k-1\"}," + BODY + "}";
  private static final String ENVELOPE_9_2 = "{" + HEADER_9_2 + "}," + BODY + "}";

  private final EnvelopeReader reader = new EnvelopeReader(JsonMapper.builder().build());

  record Sample(String name) {}

  @Test
  void should_expose_event_id_and_idempotency_key_when_envelope_carries_them() {
    ReceivedEnvelope<Sample> envelope = reader.read(record(ENVELOPE_9_3), Sample.class);

    assertThat(envelope.eventId()).isEqualTo("evt-1");
    assertThat(envelope.idempotencyKey()).isEqualTo("k-1");
    assertThat(envelope.payload()).isEqualTo(new Sample("widget"));
    assertThat(envelope.schemaVersion()).isEqualTo("2.0.0");
  }

  @Test
  void should_prefer_envelope_event_id_when_outbox_header_is_also_present() {
    ConsumerRecord<String, String> record = record(ENVELOPE_9_3);
    stringHeader(record, MessagePublisherPort.OUTBOX_ID_HEADER, "row-1");

    assertThat(reader.read(record, Sample.class).eventId()).isEqualTo("evt-1");
  }

  @Test
  void should_fall_back_to_outbox_message_id_when_envelope_has_no_event_id() {
    ConsumerRecord<String, String> record = record(ENVELOPE_9_2);
    stringHeader(record, MessagePublisherPort.OUTBOX_ID_HEADER, "row-1");

    ReceivedEnvelope<Sample> envelope = reader.read(record, Sample.class);

    assertThat(envelope.eventId()).isEqualTo("row-1");
    assertThat(envelope.outboxMessageId()).isEqualTo("row-1");
    assertThat(envelope.idempotencyKey()).isNull();
  }

  @Test
  void should_return_null_event_id_when_envelope_and_record_lack_it() {
    assertThat(reader.read(record(ENVELOPE_9_2), Sample.class).eventId()).isNull();
  }

  @Test
  void should_ignore_unknown_fields_when_application_mapper_fails_on_them() {
    EnvelopeReader strict =
        new EnvelopeReader(
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build());
    String value =
        ENVELOPE_9_3
            .replace("\"header\":{", "\"header\":{\"extra\":1,")
            .replace("\"payload\":{", "\"payload\":{\"unknown\":true,");

    assertThat(strict.read(record(value), Sample.class).payload()).isEqualTo(new Sample("widget"));
  }

  @Test
  void should_expose_traceparent_when_record_carries_it() {
    ConsumerRecord<String, String> record = record(ENVELOPE_9_3);
    stringHeader(record, MessagePublisherPort.TRACEPARENT_HEADER, "00-abc-def-01");

    assertThat(reader.read(record, Sample.class).traceparent()).isEqualTo("00-abc-def-01");
  }

  @Test
  void should_return_null_traceparent_when_record_lacks_it() {
    assertThat(reader.read(record(ENVELOPE_9_3), Sample.class).traceparent()).isNull();
  }

  @Test
  void should_read_attempt_when_retry_header_is_a_four_byte_int() {
    ConsumerRecord<String, String> record = record(ENVELOPE_9_3);
    record.headers().add(ATTEMPTS_HEADER, ByteBuffer.allocate(4).putInt(3).array());

    assertThat(reader.read(record, Sample.class).attempt()).isEqualTo(3);
  }

  @Test
  void should_read_attempt_when_retry_header_is_a_single_byte() {
    ConsumerRecord<String, String> record = record(ENVELOPE_9_3);
    record.headers().add(ATTEMPTS_HEADER, new byte[] {2});

    assertThat(reader.read(record, Sample.class).attempt()).isEqualTo(2);
  }

  @Test
  void should_default_attempt_to_one_when_retry_header_is_absent() {
    assertThat(reader.read(record(ENVELOPE_9_3), Sample.class).attempt()).isEqualTo(1);
  }

  @Test
  void should_default_attempt_to_one_when_retry_header_has_unexpected_length() {
    ConsumerRecord<String, String> record = record(ENVELOPE_9_3);
    record.headers().add(ATTEMPTS_HEADER, new byte[] {0, 3});

    assertThat(reader.read(record, Sample.class).attempt()).isEqualTo(1);
  }

  @Test
  void should_not_echo_input_when_value_is_malformed() {
    UnreadableEnvelopeException failure = unreadable("{not json");

    assertThat(failure).hasMessage("Unreadable event envelope").hasNoCause();
    assertThat(failure.getMessage()).doesNotContain("not json");
    assertThat(failure.causeClass()).startsWith("tools.jackson.");
  }

  @Test
  void should_reject_envelope_when_header_and_body_are_missing() {
    assertThat(unreadable("{}").causeClass()).isEqualTo("missing-header-or-body");
  }

  @Test
  void should_reject_envelope_when_value_is_json_null() {
    assertThat(unreadable("null").causeClass()).isEqualTo("missing-header-or-body");
  }

  @Test
  void should_reject_envelope_when_record_value_is_null() {
    assertThat(unreadable(null).causeClass()).isEqualTo("missing-header-or-body");
  }

  @Test
  void should_reject_envelope_when_body_is_missing() {
    assertThat(unreadable("{" + HEADER_9_2 + "}}").causeClass())
        .isEqualTo("missing-header-or-body");
  }

  @Test
  void should_print_only_event_id_and_attempt_when_converted_to_string() {
    String text = reader.read(record(ENVELOPE_9_3), Sample.class).toString();

    assertThat(text).contains("evt-1").contains("1").doesNotContain("widget").doesNotContain("k-1");
  }

  private UnreadableEnvelopeException unreadable(String value) {
    return catchThrowableOfType(
        UnreadableEnvelopeException.class, () -> reader.read(record(value), Sample.class));
  }

  private static ConsumerRecord<String, String> record(String value) {
    return new ConsumerRecord<>(TOPIC, 0, 0L, "key", value);
  }

  private static void stringHeader(ConsumerRecord<String, String> record, String name, String v) {
    record.headers().add(name, v.getBytes(StandardCharsets.UTF_8));
  }
}
