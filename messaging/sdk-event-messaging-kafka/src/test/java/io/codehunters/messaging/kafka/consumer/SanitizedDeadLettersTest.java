package io.codehunters.messaging.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.messaging.MessagePublisherPort;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer.HeaderNames;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.TimestampedException;
import org.springframework.kafka.support.KafkaHeaders;

class SanitizedDeadLettersTest {

  private static final String TOPIC = "orders.in";
  private static final String SECRET = "secret-value";
  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";
  private static final String ENVELOPE_WITH_EVENT_ID =
      "{\"header\":{\"eventId\":\"evt-1\"},\"body\":{\"payload\":{}}}";
  private static final TopicPartition DLT = new TopicPartition(TOPIC + ".DLT", 0);
  private static final TopicPartition RETRY_0 = new TopicPartition(TOPIC + "-retry-0", 0);
  private static final HeaderNames NAMES =
      HeaderNames.Builder.original()
          .offsetHeader(KafkaHeaders.ORIGINAL_OFFSET)
          .timestampHeader(KafkaHeaders.ORIGINAL_TIMESTAMP)
          .timestampTypeHeader(KafkaHeaders.ORIGINAL_TIMESTAMP_TYPE)
          .topicHeader(KafkaHeaders.ORIGINAL_TOPIC)
          .partitionHeader(KafkaHeaders.ORIGINAL_PARTITION)
          .consumerGroupHeader(KafkaHeaders.ORIGINAL_CONSUMER_GROUP)
          .exception()
          .keyExceptionFqcn(KafkaHeaders.KEY_EXCEPTION_FQCN)
          .exceptionFqcn(KafkaHeaders.EXCEPTION_FQCN)
          .exceptionCauseFqcn(KafkaHeaders.EXCEPTION_CAUSE_FQCN)
          .keyExceptionMessage(KafkaHeaders.KEY_EXCEPTION_MESSAGE)
          .exceptionMessage(KafkaHeaders.EXCEPTION_MESSAGE)
          .keyExceptionStacktrace(KafkaHeaders.KEY_EXCEPTION_STACKTRACE)
          .exceptionStacktrace(KafkaHeaders.EXCEPTION_STACKTRACE)
          .build();

  private final List<DeadLetter> deadLetters = new ArrayList<>();
  private final SanitizedDeadLetters sanitized =
      new SanitizedDeadLetters(List.of(deadLetters::add));

  @Test
  void should_name_listener_exception_class_when_creating_exception_headers() {
    Headers headers = new RecordHeaders();

    SanitizedDeadLetters.classNamesOnly(
        headers, wrapped(new IllegalStateException(SECRET)), false, NAMES);

    assertThat(text(headers, KafkaHeaders.EXCEPTION_FQCN))
        .isEqualTo(IllegalStateException.class.getName());
  }

  @Test
  void should_name_root_cause_class_when_listener_exception_has_a_cause() {
    Headers headers = new RecordHeaders();
    Exception failure = new IllegalStateException(SECRET, new ArithmeticException(SECRET));

    SanitizedDeadLetters.classNamesOnly(headers, wrapped(failure), false, NAMES);

    assertThat(text(headers, KafkaHeaders.EXCEPTION_CAUSE_FQCN))
        .isEqualTo(ArithmeticException.class.getName());
  }

  @Test
  void should_write_no_cause_header_when_listener_exception_has_no_cause() {
    Headers headers = new RecordHeaders();

    SanitizedDeadLetters.classNamesOnly(
        headers, wrapped(new IllegalStateException(SECRET)), false, NAMES);

    assertThat(headers.lastHeader(KafkaHeaders.EXCEPTION_CAUSE_FQCN)).isNull();
  }

  @Test
  void should_write_no_message_or_stack_trace_when_creating_exception_headers() {
    Headers headers = new RecordHeaders();

    SanitizedDeadLetters.classNamesOnly(
        headers, wrapped(new IllegalStateException(SECRET)), false, NAMES);

    assertThat(headers.toArray())
        .extracting(Header::key)
        .containsExactly(KafkaHeaders.EXCEPTION_FQCN);
  }

  @Test
  void should_strip_previous_message_and_stack_trace_when_record_carries_them() {
    Headers headers = new RecordHeaders();
    headers.add(KafkaHeaders.EXCEPTION_MESSAGE, bytes(SECRET));
    headers.add(KafkaHeaders.EXCEPTION_STACKTRACE, bytes(SECRET));
    headers.add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, bytes(SECRET));
    headers.add(KafkaHeaders.DLT_EXCEPTION_STACKTRACE, bytes(SECRET));

    SanitizedDeadLetters.classNamesOnly(
        headers, wrapped(new IllegalStateException()), false, NAMES);

    assertThat(headers.toArray())
        .extracting(Header::key)
        .containsExactly(KafkaHeaders.EXCEPTION_FQCN);
  }

  @Test
  void should_replace_previous_class_name_when_record_carries_one() {
    Headers headers = new RecordHeaders();
    headers.add(KafkaHeaders.EXCEPTION_FQCN, bytes(RuntimeException.class.getName()));

    SanitizedDeadLetters.classNamesOnly(
        headers, wrapped(new IllegalStateException()), false, NAMES);

    assertThat(headers.headers(KafkaHeaders.EXCEPTION_FQCN)).hasSize(1);
    assertThat(text(headers, KafkaHeaders.EXCEPTION_FQCN))
        .isEqualTo(IllegalStateException.class.getName());
  }

  @Test
  void should_name_key_exception_class_when_key_failed() {
    Headers headers = new RecordHeaders();

    SanitizedDeadLetters.classNamesOnly(headers, new IllegalStateException(SECRET), true, NAMES);

    assertThat(text(headers, KafkaHeaders.KEY_EXCEPTION_FQCN))
        .isEqualTo(IllegalStateException.class.getName());
  }

  @Test
  void should_add_event_id_from_envelope_when_header_carries_one() {
    ConsumerRecord<String, String> record = record(ENVELOPE_WITH_EVENT_ID);
    record.headers().add(MessagePublisherPort.OUTBOX_ID_HEADER, bytes("row-1"));

    Headers headers = SanitizedDeadLetters.correlationHeaders(record, new RuntimeException());

    assertThat(text(headers, DeadLetterHook.EVENT_ID_HEADER)).isEqualTo("evt-1");
  }

  @Test
  void should_add_outbox_message_id_as_event_id_when_envelope_has_none() {
    ConsumerRecord<String, String> record = record("{\"header\":{},\"body\":{}}");
    record.headers().add(MessagePublisherPort.OUTBOX_ID_HEADER, bytes("row-1"));

    Headers headers = SanitizedDeadLetters.correlationHeaders(record, new RuntimeException());

    assertThat(text(headers, DeadLetterHook.EVENT_ID_HEADER)).isEqualTo("row-1");
  }

  @Test
  void should_add_outbox_message_id_as_event_id_when_value_is_not_json() {
    ConsumerRecord<String, String> record = record("not json " + SECRET);
    record.headers().add(MessagePublisherPort.OUTBOX_ID_HEADER, bytes("row-1"));

    Headers headers = SanitizedDeadLetters.correlationHeaders(record, new RuntimeException());

    assertThat(text(headers, DeadLetterHook.EVENT_ID_HEADER)).isEqualTo("row-1");
  }

  @Test
  void should_add_no_event_id_when_record_carries_none() {
    Headers headers = SanitizedDeadLetters.correlationHeaders(record(null), new RuntimeException());

    assertThat(headers.lastHeader(DeadLetterHook.EVENT_ID_HEADER)).isNull();
  }

  @Test
  void should_add_trace_id_when_record_carries_valid_traceparent() {
    ConsumerRecord<String, String> record = record(ENVELOPE_WITH_EVENT_ID);
    record.headers().add(MessagePublisherPort.TRACEPARENT_HEADER, bytes(TRACEPARENT));

    Headers headers = SanitizedDeadLetters.correlationHeaders(record, new RuntimeException());

    assertThat(text(headers, DeadLetterHook.TRACE_ID_HEADER)).isEqualTo(TRACE_ID);
  }

  @Test
  void should_add_no_trace_id_when_traceparent_is_invalid() {
    ConsumerRecord<String, String> record = record(ENVELOPE_WITH_EVENT_ID);
    record.headers().add(MessagePublisherPort.TRACEPARENT_HEADER, bytes("garbage"));

    Headers headers = SanitizedDeadLetters.correlationHeaders(record, new RuntimeException());

    assertThat(headers.lastHeader(DeadLetterHook.TRACE_ID_HEADER)).isNull();
  }

  @Test
  void should_call_hook_with_listener_exception_when_target_is_dlt() {
    ConsumerRecord<String, String> record = record(ENVELOPE_WITH_EVENT_ID);
    IllegalStateException failure = new IllegalStateException(SECRET);

    resolvingTo(DLT).apply(record, new TimestampedException(wrapped(failure)));

    assertThat(deadLetters).containsExactly(new DeadLetter(record, failure));
  }

  @Test
  void should_return_resolved_target_when_target_is_dlt() {
    assertThat(resolvingTo(DLT).apply(record(null), wrapped(new IllegalStateException())))
        .isEqualTo(DLT);
  }

  @Test
  void should_not_call_hook_when_target_is_retry_topic() {
    resolvingTo(RETRY_0).apply(record(null), wrapped(new RuntimeException()));

    assertThat(deadLetters).isEmpty();
  }

  @Test
  void should_not_call_hook_when_no_target_is_resolved() {
    resolvingTo(null).apply(record(null), wrapped(new RuntimeException()));

    assertThat(deadLetters).isEmpty();
  }

  @Test
  void should_propagate_hook_failure_when_hook_throws() {
    SanitizedDeadLetters failing =
        new SanitizedDeadLetters(
            List.of(
                deadLetter -> {
                  throw new IllegalStateException("hook down");
                }));

    assertThatThrownBy(
            () -> failing.hooking((r, e) -> DLT).apply(record(null), new RuntimeException()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void should_stop_unwrapping_when_wrappers_are_nested_too_deep() {
    Exception failure = nested(new IllegalStateException(), 12);

    assertThat(SanitizedDeadLetters.listenerFailure(failure))
        .isInstanceOf(ListenerExecutionFailedException.class);
  }

  @Test
  void should_print_position_and_failure_class_only_when_dead_letter_is_printed() {
    DeadLetter deadLetter = new DeadLetter(record(SECRET), new IllegalStateException(SECRET));

    assertThat(deadLetter.toString())
        .contains(TOPIC + "-0@42")
        .contains(IllegalStateException.class.getName())
        .doesNotContain(SECRET);
  }

  private BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> resolvingTo(
      TopicPartition target) {
    return sanitized.hooking((record, exception) -> target);
  }

  private static ListenerExecutionFailedException wrapped(Exception failure) {
    return new ListenerExecutionFailedException("Listener failed", "group", failure);
  }

  private static Exception nested(Exception failure, int depth) {
    return depth == 0 ? failure : wrapped(nested(failure, depth - 1));
  }

  private static ConsumerRecord<String, String> record(String value) {
    return new ConsumerRecord<>(TOPIC, 0, 42L, "k-1", value);
  }

  private static String text(Headers headers, String key) {
    return new String(headers.lastHeader(key).value(), StandardCharsets.UTF_8);
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
