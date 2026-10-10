package io.codehunters.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.codehunters.messaging.MessagePublishException;
import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxDispatchStore;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.SendOutboxMessagesPort;
import io.codehunters.messaging.config.EventMessagingCoreAutoConfiguration;
import io.codehunters.messaging.kafka.config.EventMessagingKafkaAutoConfiguration;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.testcontainers.kafka.KafkaContainer;

class KafkaMessagePublisherAdapterIT {

  static final String KAFKA_IMAGE = "apache/kafka:4.1.1";

  private static final KafkaContainer KAFKA = new KafkaContainer(KAFKA_IMAGE);
  private static final Duration RECEIVE_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration DRAIN_WINDOW = Duration.ofSeconds(2);
  private static final String UNREACHABLE = "localhost:1";
  private static final Duration UNREACHABLE_BOUND = Duration.ofSeconds(4);
  private static final String KEY = "k-1";
  private static final String PAYLOAD = "{\"extractionId\":\"k-1\"}";
  private static final String STORED_TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String STORED_TRACEPARENT = "00-" + STORED_TRACE + "-00f067aa0ba902b7-01";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  EventMessagingKafkaAutoConfiguration.class,
                  EventMessagingCoreAutoConfiguration.class));

  @BeforeAll
  static void startBroker() {
    KAFKA.start();
  }

  @AfterAll
  static void stopBroker() {
    KAFKA.stop();
  }

  @Test
  void should_deliver_exactly_one_keyed_record_with_id_header_when_message_published() {
    String topic = "outbox-it-" + UUID.randomUUID();
    UUID id = UUID.randomUUID();

    publishThroughOutbox(StringSerializer.class, message(topic, id));

    List<ConsumerRecord<String, String>> records = consumeAll(topic);
    assertThat(records).hasSize(1);
    ConsumerRecord<String, String> record = records.getFirst();
    assertThat(record.key()).isEqualTo(KEY);
    assertThat(record.value()).isEqualTo(PAYLOAD);
    assertThat(idHeader(record)).isEqualTo(id.toString());
  }

  @Test
  void should_deliver_payload_verbatim_when_application_producer_serializes_json() {
    String topic = "outbox-it-" + UUID.randomUUID();

    publishThroughOutbox(JacksonJsonSerializer.class, message(topic, UUID.randomUUID()));

    assertThat(consumeAll(topic)).extracting(ConsumerRecord::value).containsExactly(PAYLOAD);
  }

  @Test
  void should_fail_within_send_timeout_when_broker_unreachable() {
    contextRunner
        .withPropertyValues("codehunters.outbox.publisher.kafka.send-timeout=2s")
        .withBean(ProducerFactory.class, () -> producersFor(UNREACHABLE, StringSerializer.class))
        .run(
            context -> {
              MessagePublisherPort publisher = context.getBean(MessagePublisherPort.class);
              assertTimeoutPreemptively(
                  UNREACHABLE_BOUND,
                  () ->
                      assertThatThrownBy(
                              () -> publisher.publish(message("unreachable", UUID.randomUUID())))
                          .isInstanceOf(MessagePublishException.class));
            });
  }

  @Test
  void should_deliver_stored_traceparent_when_dispatch_runs_under_another_trace() {
    String topic = "outbox-it-" + UUID.randomUUID();

    dispatchUnderAnotherTrace(contextRunner, topic);

    ConsumerRecord<String, String> record = consumeAll(topic).getFirst();
    assertThat(traceIdOf(header(record, MessagePublisherPort.TRACEPARENT_HEADER)))
        .isEqualTo(STORED_TRACE);
    assertThat(header(record, MessagePublisherPort.TRACEPARENT_HEADER))
        .isEqualTo(STORED_TRACEPARENT);
  }

  @Test
  void should_deliver_no_legacy_traceparent_when_switch_is_off() {
    String topic = "outbox-it-" + UUID.randomUUID();

    dispatchUnderAnotherTrace(contextRunner, topic);

    assertThat(
            consumeAll(topic)
                .getFirst()
                .headers()
                .lastHeader(MessagePublisherPort.LEGACY_TRACEPARENT_HEADER))
        .isNull();
  }

  @Test
  void should_deliver_legacy_traceparent_when_switch_is_on() {
    String topic = "outbox-it-" + UUID.randomUUID();

    dispatchUnderAnotherTrace(
        contextRunner.withPropertyValues(
            "codehunters.outbox.publisher.legacy-traceparent-header=true"),
        topic);

    assertThat(header(consumeAll(topic).getFirst(), MessagePublisherPort.LEGACY_TRACEPARENT_HEADER))
        .isEqualTo(STORED_TRACEPARENT);
  }

  // A scheduled dispatch: the scheduler's own span is current and its trace id is in the MDC.
  private static void dispatchUnderAnotherTrace(ApplicationContextRunner runner, String topic) {
    try (SdkTracerProvider provider = SdkTracerProvider.builder().build()) {
      Tracer tracer = otelTracer(provider);
      runner
          .withBean(ProducerFactory.class, () -> applicationProducers(StringSerializer.class))
          .withBean(OutboxDispatchStore.class, () -> claimingTraced(topic))
          .withBean(Tracer.class, () -> tracer)
          .run(
              context ->
                  dispatchInSchedulerSpan(tracer, context.getBean(SendOutboxMessagesPort.class)));
    }
  }

  private static void dispatchInSchedulerSpan(Tracer tracer, SendOutboxMessagesPort dispatch) {
    Span scheduler = tracer.nextSpan().name("scheduled-dispatch").start();
    try (Tracer.SpanInScope inScope = tracer.withSpan(scheduler);
        MDC.MDCCloseable mdc = MDC.putCloseable("traceId", scheduler.context().traceId())) {
      dispatch.execute();
    } finally {
      scheduler.end();
    }
  }

  private static Tracer otelTracer(SdkTracerProvider provider) {
    OtelCurrentTraceContext current = new OtelCurrentTraceContext();
    return new OtelTracer(
        provider.get("it"),
        current,
        event -> {},
        new OtelBaggageManager(current, List.of(), List.of()));
  }

  private static OutboxDispatchStore claimingTraced(String topic) {
    OutboxMessage<String> stored =
        message(topic, UUID.randomUUID()).toBuilder().traceparent(STORED_TRACEPARENT).build();
    OutboxDispatchStore repository = mock(OutboxDispatchStore.class);
    when(repository.claim(any())).thenReturn(List.of(stored));
    when(repository.markSendStarted(any())).thenReturn(Optional.of(stored));
    return repository;
  }

  private static String header(ConsumerRecord<String, String> record, String name) {
    return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
  }

  private static String traceIdOf(String traceparent) {
    return traceparent.split("-")[1];
  }

  @SuppressWarnings("rawtypes")
  private void publishThroughOutbox(
      Class<? extends Serializer> valueSerializer, OutboxMessage<String> message) {
    contextRunner
        .withBean(ProducerFactory.class, () -> applicationProducers(valueSerializer))
        .run(context -> context.getBean(MessagePublisherPort.class).publish(message));
  }

  @SuppressWarnings("rawtypes")
  private static ProducerFactory<Object, Object> applicationProducers(
      Class<? extends Serializer> valueSerializer) {
    return producersFor(KAFKA.getBootstrapServers(), valueSerializer);
  }

  @SuppressWarnings("rawtypes")
  private static ProducerFactory<Object, Object> producersFor(
      String bootstrapServers, Class<? extends Serializer> valueSerializer) {
    return new DefaultKafkaProducerFactory<>(
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            valueSerializer));
  }

  private static List<ConsumerRecord<String, String>> consumeAll(String topic) {
    List<ConsumerRecord<String, String>> records = new ArrayList<>();
    try (var consumer = new KafkaConsumer<String, String>(consumerProps())) {
      consumer.subscribe(List.of(topic));
      long deadline = System.nanoTime() + RECEIVE_TIMEOUT.toNanos();
      while (records.isEmpty() && System.nanoTime() < deadline) {
        consumer.poll(Duration.ofMillis(250)).forEach(records::add);
      }
      consumer.poll(DRAIN_WINDOW).forEach(records::add);
    }
    return records;
  }

  private static String idHeader(ConsumerRecord<String, String> record) {
    byte[] value = record.headers().lastHeader(MessagePublisherPort.OUTBOX_ID_HEADER).value();
    return new String(value, StandardCharsets.UTF_8);
  }

  private static OutboxMessage<String> message(String topic, UUID id) {
    return OutboxMessage.<String>builder().id(id).topic(topic).key(KEY).message(PAYLOAD).build();
  }

  private static Map<String, Object> consumerProps() {
    return Map.of(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        KAFKA.getBootstrapServers(),
        ConsumerConfig.GROUP_ID_CONFIG,
        "outbox-it-" + UUID.randomUUID(),
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
        "earliest",
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        StringDeserializer.class,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        StringDeserializer.class);
  }
}
