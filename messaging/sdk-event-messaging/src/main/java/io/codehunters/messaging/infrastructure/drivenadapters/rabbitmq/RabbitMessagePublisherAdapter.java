package io.codehunters.messaging.infrastructure.drivenadapters.rabbitmq;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * {@link MessagePublisherPort} backed by a Spring AMQP {@link RabbitTemplate}. Sends persistent
 * JSON messages over the configured exchange with {@code contentType=application/json} and {@code
 * contentEncoding=UTF-8} pinned for every outgoing message.
 */
@Slf4j
public class RabbitMessagePublisherAdapter implements MessagePublisherPort {

  /**
   * Pinned content type for every message produced by this adapter. The SDK serializes payloads
   * exclusively as JSON; this value resolves to the literal {@code "application/json"} and MUST NOT
   * be overridden per call.
   */
  private static final String CONTENT_TYPE = MessageProperties.CONTENT_TYPE_JSON;

  private final RabbitTemplate rabbitTemplate;

  /**
   * Creates the adapter.
   *
   * @param rabbitTemplate the underlying {@link RabbitTemplate}
   */
  public RabbitMessagePublisherAdapter(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  @Override
  public void publish(String topic, String routingKey, String payload) {
    log.debug("Publishing message to topic: {}, routingKey: {}", topic, routingKey);
    rabbitTemplate.send(topic, routingKey, jsonMessage(payload));
    log.debug("Message published successfully to topic: {}, routingKey: {}", topic, routingKey);
  }

  @Override
  public void publish(String topic, String payload) {
    log.debug("Publishing message to topic: {}", topic);
    rabbitTemplate.send(topic, "", jsonMessage(payload));
    log.debug("Message published successfully to topic: {}", topic);
  }

  /**
   * Builds an AMQP {@link Message} with {@code contentType=application/json}, {@code
   * contentEncoding=UTF-8} and persistent delivery mode. The payload is encoded as UTF-8 bytes; no
   * further serialization is applied.
   *
   * @param payload the JSON payload to wrap
   * @return the AMQP message ready to send
   */
  private Message jsonMessage(String payload) {
    MessageProperties props = new MessageProperties();
    props.setContentType(CONTENT_TYPE);
    props.setContentEncoding(StandardCharsets.UTF_8.name());
    props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    return new Message(payload.getBytes(StandardCharsets.UTF_8), props);
  }
}
