package io.codehunters.messaging.kafka;

import io.codehunters.messaging.MessagePublishException;
import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * {@link MessagePublisherPort} backed by a Spring Kafka {@link KafkaTemplate}.
 *
 * <p>{@link #publish(OutboxMessage)} sends the outbox row keyed by its {@code message_key}, so
 * every event of one aggregate lands on the same partition in order. Each record carries a {@code
 * content-type: application/json} header and an {@value MessagePublisherPort#OUTBOX_ID_HEADER}
 * header with the outbox row id; delivery is at-least-once, so consumers MUST dedupe on that
 * header.
 *
 * <p>Every send, including any wait inside {@link KafkaTemplate#send} for metadata or buffer space,
 * blocks for at most {@code sendTimeout}. The dispatcher publishes a claimed batch sequentially, so
 * deployments MUST keep {@code batchSize × sendTimeout < claim.ttlMs}; otherwise another instance
 * re-claims rows still being published and publishes them twice. Failures, timeouts and interrupts
 * surface as {@link MessagePublishException}, whose message never carries broker text.
 */
@Slf4j
public class KafkaMessagePublisherAdapter implements MessagePublisherPort {

  private static final String CONTENT_TYPE_HEADER = "content-type";
  private static final byte[] CONTENT_TYPE_JSON =
      "application/json".getBytes(StandardCharsets.UTF_8);

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final Duration sendTimeout;

  /**
   * Creates the adapter.
   *
   * @param kafkaTemplate the underlying {@link KafkaTemplate} with String key and value serializers
   * @param sendTimeout the maximum wait for the broker acknowledgement of one record
   */
  public KafkaMessagePublisherAdapter(
      KafkaTemplate<String, String> kafkaTemplate, Duration sendTimeout) {
    this.kafkaTemplate = kafkaTemplate;
    this.sendTimeout = sendTimeout;
  }

  /**
   * Sends the message keyed by {@code message_key} with the content-type and outbox id headers.
   *
   * @param message the claimed outbox message
   */
  @Override
  public void publish(OutboxMessage<String> message) {
    ProducerRecord<String, String> record =
        jsonRecord(message.getTopic(), message.getKey(), message.getMessage());
    record
        .headers()
        .add(OUTBOX_ID_HEADER, message.getId().toString().getBytes(StandardCharsets.UTF_8));
    log.debug(
        "Publishing outbox message to topic: {}, id: {}", message.getTopic(), message.getId());
    send(record);
  }

  /**
   * Sends without a key, so records are spread across partitions and unordered. The routing key is
   * an AMQP concept and is ignored.
   */
  @Override
  public void publish(String topic, String routingKey, String payload) {
    publish(topic, payload);
  }

  /** Sends without a key, so records are spread across partitions and unordered. */
  @Override
  public void publish(String topic, String payload) {
    log.debug("Publishing unkeyed message to topic: {}", topic);
    send(jsonRecord(topic, null, payload));
  }

  private static ProducerRecord<String, String> jsonRecord(
      String topic, String key, String payload) {
    ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
    record.headers().add(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON);
    return record;
  }

  // send() itself may block (metadata, full buffer) for up to max.block.ms; the acknowledgement
  // wait gets only what is left of the send timeout, so one publish never exceeds it.
  private void send(ProducerRecord<String, String> record) {
    long deadline = System.nanoTime() + sendTimeout.toNanos();
    try {
      var acknowledgement = kafkaTemplate.send(record);
      acknowledgement.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new MessagePublishException(e);
    } catch (ExecutionException
        | TimeoutException
        | KafkaException
        | org.springframework.kafka.KafkaException e) {
      throw new MessagePublishException(e);
    }
  }
}
