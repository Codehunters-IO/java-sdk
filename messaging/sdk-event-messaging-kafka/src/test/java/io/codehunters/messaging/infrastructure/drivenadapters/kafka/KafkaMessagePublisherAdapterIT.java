package io.codehunters.messaging.infrastructure.drivenadapters.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.config.EventMessagingCoreAutoConfiguration;
import io.codehunters.messaging.config.EventMessagingKafkaAutoConfiguration;
import io.codehunters.messaging.domain.model.OutboxMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
  private static final String KEY = "k-1";
  private static final String PAYLOAD = "{\"extractionId\":\"k-1\"}";

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
    return new DefaultKafkaProducerFactory<>(
        Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KAFKA.getBootstrapServers(),
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
