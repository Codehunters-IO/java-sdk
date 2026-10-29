package io.codehunters.messaging.kafka.consumer;

import static io.codehunters.messaging.kafka.consumer.InboundBroker.DLT_SUFFIX;
import static io.codehunters.messaging.kafka.consumer.InboundBroker.text;
import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.kafka.consumer.InboundListenerConfig.Behaviour;
import io.codehunters.messaging.kafka.consumer.config.EventMessagingKafkaConsumerAutoConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.retrytopic.RetryTopicHeaders;
import org.springframework.kafka.support.KafkaHeaders;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.json.JsonMapper;

class SanitizedDeadLetterIT {

  private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.1");
  private static final Duration RECEIVE_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration QUIET_WINDOW = Duration.ofSeconds(2);
  private static final String SECRET = "secret-value";
  private static final String EVENT_ID = "evt-1";
  private static final String ROW_ID = "row-1";
  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";
  private static final String ENVELOPE =
      "{\"header\":{\"eventId\":\""
          + EVENT_ID
          + "\",\"idempotencyKey\":\"k-1\"},"
          + "\"body\":{\"eventName\":\"OrderPlaced\",\"payload\":{\"name\":\"widget\"}}}";
  private static final Map<String, String> HEADERS =
      Map.of(
          MessagePublisherPort.TRACEPARENT_HEADER,
          TRACEPARENT,
          MessagePublisherPort.OUTBOX_ID_HEADER,
          ROW_ID);

  private static InboundBroker broker;

  @BeforeAll
  static void startBroker() {
    KAFKA.start();
    broker = new InboundBroker(KAFKA.getBootstrapServers());
  }

  @AfterAll
  static void stopBroker() {
    KAFKA.stop();
  }

  @Test
  void should_dead_letter_once_without_retries_when_exception_is_not_retryable() throws Exception {
    String topic = topology();
    RecordingHook hook = new RecordingHook();

    application(topic, failingWith(new IllegalStateException(SECRET)), hook)
        .run(
            context -> {
              broker.send(topic, ENVELOPE, HEADERS);

              assertThat(broker.awaitRecords(topic + DLT_SUFFIX, RECEIVE_TIMEOUT)).hasSize(1);
              assertThat(broker.awaitRecords(topic + "-retry-0", QUIET_WINDOW)).isEmpty();
              assertThat(hook.failureClasses())
                  .containsExactly(IllegalStateException.class.getName());
            });
  }

  @Test
  void should_hop_through_every_retry_topic_then_dead_letter_when_exception_is_retryable()
      throws Exception {
    String topic = topology();
    RecordingHook hook = new RecordingHook();

    application(topic, failingWith(new RuntimeException(SECRET)), hook)
        .run(
            context -> {
              broker.send(topic, ENVELOPE, HEADERS);

              assertThat(broker.awaitRecords(topic + DLT_SUFFIX, RECEIVE_TIMEOUT)).hasSize(1);
              assertThat(broker.awaitRecords(topic + "-retry-0", RECEIVE_TIMEOUT)).hasSize(1);
              assertThat(broker.awaitRecords(topic + "-retry-1", RECEIVE_TIMEOUT)).hasSize(1);
              assertThat(broker.awaitRecords(topic + "-retry-2", RECEIVE_TIMEOUT)).hasSize(1);
              assertThat(hook.failureClasses()).containsExactly(RuntimeException.class.getName());
            });
  }

  @Test
  void should_forward_sanitized_headers_and_original_value_on_every_hop_when_record_fails()
      throws Exception {
    String topic = topology();

    application(topic, failingWith(new RuntimeException(SECRET)), new RecordingHook())
        .run(
            context -> {
              broker.send(topic, ENVELOPE, HEADERS);

              assertThat(forwarded(topic))
                  .hasSize(4)
                  .allSatisfy(SanitizedDeadLetterIT::assertSanitized);
            });
  }

  @Test
  void should_dead_letter_without_retries_when_envelope_is_unreadable() throws Exception {
    String topic = topology();
    RecordingHook hook = new RecordingHook();
    EnvelopeReader reader = new EnvelopeReader(JsonMapper.builder().build());

    application(topic, record -> reader.read(record, Map.class), hook)
        .run(
            context -> {
              broker.send(topic, "not an envelope " + SECRET, HEADERS);

              List<ConsumerRecord<byte[], byte[]>> dlt =
                  broker.awaitRecords(topic + DLT_SUFFIX, RECEIVE_TIMEOUT);
              assertThat(dlt).hasSize(1);
              assertThat(text(dlt.getFirst().headers().lastHeader(KafkaHeaders.EXCEPTION_FQCN)))
                  .isEqualTo(UnreadableEnvelopeException.class.getName());
              assertThat(broker.awaitRecords(topic + "-retry-0", QUIET_WINDOW)).isEmpty();
              assertThat(hook.failureClasses())
                  .containsExactly(UnreadableEnvelopeException.class.getName());
            });
  }

  @Test
  void should_publish_dead_letter_only_after_hook_succeeds_when_hook_fails_first()
      throws Exception {
    String topic = topology();
    FailingOnceHook hook = new FailingOnceHook();

    application(topic, failingWith(new IllegalStateException(SECRET)), hook)
        .run(
            context -> {
              broker.send(topic, ENVELOPE, HEADERS);

              assertThat(broker.awaitRecords(topic + DLT_SUFFIX, RECEIVE_TIMEOUT)).hasSize(1);
              assertThat(hook.calls).hasSize(2);
              assertThat(hook.dltPublishedBeforeSuccess).isFalse();
            });
  }

  private static void assertSanitized(ConsumerRecord<byte[], byte[]> record) {
    assertThat(record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE)).isNull();
    assertThat(record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_STACKTRACE)).isNull();
    assertThat(record.headers().lastHeader("kafka_dlt-exception-stacktrace")).isNull();
    assertThat(record.headers().lastHeader(KafkaHeaders.EXCEPTION_MESSAGE)).isNull();
    assertThat(record.headers().lastHeader(KafkaHeaders.EXCEPTION_STACKTRACE)).isNull();
    assertThat(text(record.headers().lastHeader(KafkaHeaders.EXCEPTION_FQCN)))
        .isEqualTo(RuntimeException.class.getName());
    assertThat(record.headers().toArray())
        .extracting(InboundBroker::text)
        .noneMatch(value -> value.contains(SECRET));
    assertThat(record.value()).isEqualTo(ENVELOPE.getBytes(StandardCharsets.UTF_8));
    assertThat(record.headers().toArray())
        .extracting(Header::key)
        .contains(
            KafkaHeaders.ORIGINAL_TOPIC,
            KafkaHeaders.ORIGINAL_PARTITION,
            KafkaHeaders.ORIGINAL_OFFSET,
            RetryTopicHeaders.DEFAULT_HEADER_ATTEMPTS);
    assertThat(text(record.headers().lastHeader(DeadLetterHook.EVENT_ID_HEADER)))
        .isEqualTo(EVENT_ID);
    assertThat(text(record.headers().lastHeader(DeadLetterHook.TRACE_ID_HEADER)))
        .isEqualTo(TRACE_ID);
    assertThat(text(record.headers().lastHeader(MessagePublisherPort.TRACEPARENT_HEADER)))
        .isEqualTo(TRACEPARENT);
    assertThat(text(record.headers().lastHeader(MessagePublisherPort.OUTBOX_ID_HEADER)))
        .isEqualTo(ROW_ID);
  }

  private static List<ConsumerRecord<byte[], byte[]>> forwarded(String topic) {
    List<ConsumerRecord<byte[], byte[]>> records = new ArrayList<>();
    records.addAll(broker.awaitRecords(topic + DLT_SUFFIX, RECEIVE_TIMEOUT));
    InboundBroker.RETRY_SUFFIXES.forEach(
        suffix -> records.addAll(broker.awaitRecords(topic + suffix, RECEIVE_TIMEOUT)));
    return records;
  }

  private static String topology() throws ExecutionException, InterruptedException {
    String topic = "inbound-it-" + UUID.randomUUID();
    broker.createTopology(topic);
    return topic;
  }

  private static ApplicationContextRunner application(
      String topic, Behaviour behaviour, DeadLetterHook hook) {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                KafkaAutoConfiguration.class, EventMessagingKafkaConsumerAutoConfiguration.class))
        .withUserConfiguration(InboundListenerConfig.class)
        .withPropertyValues(
            "spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
            "spring.kafka.consumer.auto-offset-reset=earliest",
            "it.topic=" + topic,
            "it.group=inbound-it-" + UUID.randomUUID())
        .withBean(Behaviour.class, () -> behaviour)
        .withBean(DeadLetterHook.class, () -> hook);
  }

  private static Behaviour failingWith(RuntimeException failure) {
    return record -> {
      throw failure;
    };
  }

  /** Records the failure class of every dead letter it sees. */
  static final class RecordingHook implements DeadLetterHook {

    private final List<String> failureClasses = new CopyOnWriteArrayList<>();

    @Override
    public void beforeDeadLetter(DeadLetter deadLetter) {
      failureClasses.add(deadLetter.failure().getClass().getName());
    }

    List<String> failureClasses() {
      return failureClasses;
    }
  }

  /**
   * Throws on its first call, as a hook whose database is briefly down would, and remembers whether
   * the dead-letter topic already held the record when it was called again.
   */
  final class FailingOnceHook implements DeadLetterHook {

    private final List<DeadLetter> calls = new CopyOnWriteArrayList<>();
    private final AtomicBoolean dltPublishedBeforeSuccess = new AtomicBoolean();

    @Override
    public void beforeDeadLetter(DeadLetter deadLetter) {
      calls.add(deadLetter);
      if (calls.size() == 1) {
        throw new IllegalStateException("hook unavailable");
      }
      String dlt = deadLetter.record().topic() + DLT_SUFFIX;
      dltPublishedBeforeSuccess.set(!broker.awaitRecords(dlt, Duration.ofMillis(500)).isEmpty());
    }
  }
}
