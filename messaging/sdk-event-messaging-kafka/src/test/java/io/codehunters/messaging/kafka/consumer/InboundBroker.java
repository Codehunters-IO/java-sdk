package io.codehunters.messaging.kafka.consumer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Broker-side fixture of the inbound ITs: creates the five topics of a retry topology, sends the
 * original record and reads what the SDK forwarded, as raw bytes.
 */
final class InboundBroker {

  static final List<String> RETRY_SUFFIXES = List.of("-retry-0", "-retry-1", "-retry-2");
  static final String DLT_SUFFIX = ".DLT";

  private static final int PARTITIONS = 3;
  private static final short REPLICAS = 1;
  private static final Duration POLL = Duration.ofMillis(250);

  private final String bootstrapServers;

  InboundBroker(String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  /** Creates the main, retry and dead-letter topics with the same partition count. */
  void createTopology(String topic) throws ExecutionException, InterruptedException {
    List<NewTopic> topics = new ArrayList<>();
    topics.add(new NewTopic(topic, PARTITIONS, REPLICAS));
    RETRY_SUFFIXES.forEach(
        suffix -> topics.add(new NewTopic(topic + suffix, PARTITIONS, REPLICAS)));
    topics.add(new NewTopic(topic + DLT_SUFFIX, PARTITIONS, REPLICAS));
    try (AdminClient admin =
        AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
      admin.createTopics(topics).all().get();
    }
  }

  void send(String topic, String value, Map<String, String> headers)
      throws ExecutionException, InterruptedException {
    ProducerRecord<String, String> record = new ProducerRecord<>(topic, "k-1", value);
    headers.forEach(
        (name, header) -> record.headers().add(name, header.getBytes(StandardCharsets.UTF_8)));
    try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
      producer.send(record).get();
    }
  }

  /** Polls until at least one record arrived or the timeout passed, then drains a short window. */
  List<ConsumerRecord<byte[], byte[]>> awaitRecords(String topic, Duration timeout) {
    List<ConsumerRecord<byte[], byte[]>> records = new ArrayList<>();
    try (var consumer = new KafkaConsumer<byte[], byte[]>(consumerProps())) {
      consumer.subscribe(List.of(topic));
      long deadline = System.nanoTime() + timeout.toNanos();
      while (records.isEmpty() && System.nanoTime() < deadline) {
        consumer.poll(POLL).forEach(records::add);
      }
      consumer.poll(Duration.ofSeconds(1)).forEach(records::add);
    }
    return records;
  }

  static String text(Header header) {
    return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
  }

  private Map<String, Object> producerProps() {
    return Map.of(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
        bootstrapServers,
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        StringSerializer.class,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        StringSerializer.class);
  }

  private Map<String, Object> consumerProps() {
    return Map.of(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        bootstrapServers,
        ConsumerConfig.GROUP_ID_CONFIG,
        "inbound-it-reader-" + UUID.randomUUID(),
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
        "earliest",
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        ByteArrayDeserializer.class,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        ByteArrayDeserializer.class);
  }
}
