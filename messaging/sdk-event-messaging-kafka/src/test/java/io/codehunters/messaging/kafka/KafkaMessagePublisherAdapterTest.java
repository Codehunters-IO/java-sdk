package io.codehunters.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.codehunters.messaging.MessagePublishException;
import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaMessagePublisherAdapterTest {

  private static final String TOPIC = "ocr.extraction.events";
  private static final String KEY = "k-1";
  private static final String PAYLOAD = "{\"a\":1}";
  private static final String PII_SENTINEL = "PII-SENTINEL-4242 holder Juan";
  private static final UUID ID = UUID.fromString("0b9c6a3e-7d1f-4a51-9f0e-2a7c1d4e5f60");
  private static final Duration SEND_TIMEOUT = Duration.ofMillis(50);
  private static final String TRACEPARENT =
      "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

  private KafkaTemplate<String, String> kafkaTemplate;
  private KafkaMessagePublisherAdapter adapter;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    kafkaTemplate = mock(KafkaTemplate.class);
    adapter = new KafkaMessagePublisherAdapter(kafkaTemplate, SEND_TIMEOUT);
  }

  @AfterEach
  void clearInterruptFlag() {
    Thread.interrupted();
  }

  @Test
  void should_send_keyed_record_with_id_and_content_type_headers_when_publishing_message() {
    givenSendReturns(CompletableFuture.completedFuture(null));

    adapter.publish(message());

    ProducerRecord<String, String> record = sentRecord();
    assertThat(record.topic()).isEqualTo(TOPIC);
    assertThat(record.key()).isEqualTo(KEY);
    assertThat(record.value()).isEqualTo(PAYLOAD);
    assertThat(header(record, MessagePublisherPort.OUTBOX_ID_HEADER)).isEqualTo(ID.toString());
    assertThat(header(record, "content-type")).isEqualTo("application/json");
  }

  @Test
  void should_throw_publish_exception_without_cause_text_when_send_fails() {
    givenSendReturns(CompletableFuture.failedFuture(new RuntimeException(PII_SENTINEL)));

    assertThatThrownBy(() -> adapter.publish(message()))
        .isInstanceOf(MessagePublishException.class)
        .hasMessage("Broker publish failed")
        .hasMessageNotContaining("PII-SENTINEL");
  }

  @Test
  void should_throw_publish_exception_caused_by_timeout_when_send_never_completes() {
    givenSendReturns(new CompletableFuture<>());

    assertThatThrownBy(() -> adapter.publish(message()))
        .isInstanceOf(MessagePublishException.class)
        .hasCauseInstanceOf(TimeoutException.class);
  }

  @Test
  void should_throw_publish_exception_when_send_blocks_past_max_block() {
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenThrow(new org.apache.kafka.common.errors.TimeoutException(PII_SENTINEL));

    assertThatThrownBy(() -> adapter.publish(message()))
        .isInstanceOf(MessagePublishException.class)
        .hasMessageNotContaining("PII-SENTINEL")
        .hasCauseInstanceOf(org.apache.kafka.common.errors.TimeoutException.class);
  }

  @Test
  void should_count_blocking_send_against_send_timeout_when_acknowledgement_never_arrives() {
    Duration sendTimeout = Duration.ofMillis(300);
    var bounded = new KafkaMessagePublisherAdapter(kafkaTemplate, sendTimeout);
    when(kafkaTemplate.send(any(ProducerRecord.class)))
        .thenAnswer(call -> blockThenReturnPending(sendTimeout));

    assertTimeoutPreemptively(
        Duration.ofMillis(500),
        () ->
            assertThatThrownBy(() -> bounded.publish(message()))
                .isInstanceOf(MessagePublishException.class));
  }

  @Test
  void should_restore_interrupt_flag_when_calling_thread_interrupted() {
    givenSendReturns(new CompletableFuture<>());
    Thread.currentThread().interrupt();

    assertThatThrownBy(() -> adapter.publish(message()))
        .isInstanceOf(MessagePublishException.class)
        .hasCauseInstanceOf(InterruptedException.class);
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  @Test
  void should_send_without_key_when_legacy_topic_publish_used() {
    givenSendReturns(CompletableFuture.completedFuture(null));

    adapter.publish(TOPIC, PAYLOAD);

    ProducerRecord<String, String> record = sentRecord();
    assertThat(record.key()).isNull();
    assertThat(record.value()).isEqualTo(PAYLOAD);
    assertThat(header(record, "content-type")).isEqualTo("application/json");
    assertThat(record.headers().lastHeader("outbox-message-id")).isNull();
  }

  @Test
  void should_send_without_key_when_legacy_routing_key_publish_used() {
    givenSendReturns(CompletableFuture.completedFuture(null));

    adapter.publish(TOPIC, "user.created", PAYLOAD);

    ProducerRecord<String, String> record = sentRecord();
    assertThat(record.topic()).isEqualTo(TOPIC);
    assertThat(record.key()).isNull();
  }

  @Test
  void should_send_stored_traceparent_header_when_message_has_one() {
    givenSendReturns(CompletableFuture.completedFuture(null));

    adapter.publish(traced(TRACEPARENT));

    assertThat(header(sentRecord(), MessagePublisherPort.TRACEPARENT_HEADER))
        .isEqualTo(TRACEPARENT);
  }

  @Test
  void should_send_no_traceparent_header_when_message_has_none() {
    givenSendReturns(CompletableFuture.completedFuture(null));

    adapter.publish(message());

    assertThat(sentRecord().headers().lastHeader(MessagePublisherPort.TRACEPARENT_HEADER)).isNull();
  }

  @Test
  void should_send_no_traceparent_header_when_stored_traceparent_is_blank() {
    givenSendReturns(CompletableFuture.completedFuture(null));

    adapter.publish(traced(""));

    assertThat(sentRecord().headers().lastHeader(MessagePublisherPort.TRACEPARENT_HEADER)).isNull();
  }

  @Test
  void should_send_no_traceparent_header_when_stored_traceparent_is_longer_than_55_characters() {
    givenSendReturns(CompletableFuture.completedFuture(null));

    new KafkaMessagePublisherAdapter(kafkaTemplate, SEND_TIMEOUT, true)
        .publish(traced(TRACEPARENT + "-extra"));

    assertThat(sentRecord().headers().lastHeader(MessagePublisherPort.TRACEPARENT_HEADER)).isNull();
    assertThat(sentRecord().headers().lastHeader(MessagePublisherPort.LEGACY_TRACEPARENT_HEADER))
        .isNull();
  }

  @Test
  void should_send_no_traceparent_header_when_stored_traceparent_is_malformed() {
    givenSendReturns(CompletableFuture.completedFuture(null));

    adapter.publish(traced("00-not-a-trace-01"));

    assertThat(sentRecord().headers().lastHeader(MessagePublisherPort.TRACEPARENT_HEADER)).isNull();
  }

  @Test
  void should_send_no_traceparent_header_when_stored_traceparent_has_version_ff() {
    givenSendReturns(CompletableFuture.completedFuture(null));

    adapter.publish(traced("ff" + TRACEPARENT.substring(2)));

    assertThat(sentRecord().headers().lastHeader(MessagePublisherPort.TRACEPARENT_HEADER)).isNull();
  }

  @Test
  void should_send_no_legacy_traceparent_header_when_switch_is_off() {
    givenSendReturns(CompletableFuture.completedFuture(null));

    adapter.publish(traced(TRACEPARENT));

    assertThat(sentRecord().headers().lastHeader(MessagePublisherPort.LEGACY_TRACEPARENT_HEADER))
        .isNull();
  }

  @Test
  void should_send_legacy_traceparent_header_when_switch_is_on() {
    givenSendReturns(CompletableFuture.completedFuture(null));

    new KafkaMessagePublisherAdapter(kafkaTemplate, SEND_TIMEOUT, true)
        .publish(traced(TRACEPARENT));

    assertThat(header(sentRecord(), MessagePublisherPort.LEGACY_TRACEPARENT_HEADER))
        .isEqualTo(TRACEPARENT);
  }

  private static OutboxMessage<String> traced(String traceparent) {
    return message().toBuilder().traceparent(traceparent).build();
  }

  private static CompletableFuture<SendResult<String, String>> blockThenReturnPending(
      Duration blocked) throws InterruptedException {
    Thread.sleep(blocked);
    return new CompletableFuture<>();
  }

  private void givenSendReturns(CompletableFuture<SendResult<String, String>> future) {
    when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);
  }

  @SuppressWarnings("unchecked")
  private ProducerRecord<String, String> sentRecord() {
    ArgumentCaptor<ProducerRecord<String, String>> captor =
        ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafkaTemplate).send(captor.capture());
    return captor.getValue();
  }

  private static String header(ProducerRecord<String, String> record, String name) {
    Header header = record.headers().lastHeader(name);
    return new String(header.value(), StandardCharsets.UTF_8);
  }

  private static OutboxMessage<String> message() {
    return OutboxMessage.<String>builder().id(ID).topic(TOPIC).key(KEY).message(PAYLOAD).build();
  }
}
