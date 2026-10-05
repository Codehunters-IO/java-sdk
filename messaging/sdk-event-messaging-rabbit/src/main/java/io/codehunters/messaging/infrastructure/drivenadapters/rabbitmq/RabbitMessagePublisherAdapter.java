package io.codehunters.messaging.infrastructure.drivenadapters.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import io.codehunters.messaging.application.port.MessagePublishException;
import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.domain.model.OutboxMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitOperations;

/**
 * {@link MessagePublisherPort} backed by a Spring AMQP {@link RabbitOperations} with at-least-once
 * delivery. Every message is persistent JSON ({@code contentType=application/json}, {@code
 * contentEncoding=UTF-8}).
 *
 * <p>Each send runs on a dedicated channel ({@link RabbitOperations#invoke}) and blocks for the
 * broker confirm for at most {@code confirmTimeout}. The template must publish with {@code
 * mandatory=true} on a connection factory with simple publisher confirms and returns. A nack, a
 * confirm timeout, a closed channel (for example a missing exchange) or a {@code basic.return} (no
 * queue bound for the routing key) surfaces as {@link MessagePublishException}, whose message never
 * carries broker text, so the dispatcher retries the row.
 *
 * <p>{@link #publish(OutboxMessage)} sets the AMQP {@code messageId} and the {@value
 * MessagePublisherPort#OUTBOX_ID_HEADER} header to the outbox row id; consumers MUST dedupe on it.
 * The dispatcher publishes a claimed batch sequentially, so deployments MUST keep {@code batchSize
 * × confirmTimeout < claim.ttlMs}.
 */
@Slf4j
public class RabbitMessagePublisherAdapter implements MessagePublisherPort {

  /**
   * Pinned content type for every message produced by this adapter. The SDK serializes payloads
   * exclusively as JSON; this value resolves to the literal {@code "application/json"} and MUST NOT
   * be overridden per call.
   */
  private static final String CONTENT_TYPE = MessageProperties.CONTENT_TYPE_JSON;

  private final RabbitOperations rabbitOperations;
  private final Duration confirmTimeout;

  /**
   * Creates the adapter.
   *
   * @param rabbitOperations the mandatory, confirming template that sends the messages
   * @param confirmTimeout the maximum wait for the broker confirm of one message
   */
  public RabbitMessagePublisherAdapter(RabbitOperations rabbitOperations, Duration confirmTimeout) {
    this.rabbitOperations = rabbitOperations;
    this.confirmTimeout = confirmTimeout;
  }

  /**
   * Sends the message with its routing key (empty when unset), tagged with the outbox row id as
   * AMQP {@code messageId} and as the {@value MessagePublisherPort#OUTBOX_ID_HEADER} header.
   *
   * @param message the claimed outbox message
   */
  @Override
  public void publish(OutboxMessage<String> message) {
    String id = message.getId().toString();
    Message amqpMessage = jsonMessage(message.getMessage());
    amqpMessage.getMessageProperties().setMessageId(id);
    amqpMessage.getMessageProperties().setHeader(OUTBOX_ID_HEADER, id);
    String routingKey = message.getRoutingKey() == null ? "" : message.getRoutingKey();
    log.debug("Publishing outbox message to exchange: {}, id: {}", message.getTopic(), id);
    send(message.getTopic(), routingKey, amqpMessage);
  }

  @Override
  public void publish(String topic, String routingKey, String payload) {
    log.debug("Publishing message to topic: {}, routingKey: {}", topic, routingKey);
    send(topic, routingKey, jsonMessage(payload));
  }

  @Override
  public void publish(String topic, String payload) {
    log.debug("Publishing message to topic: {}", topic);
    send(topic, "", jsonMessage(payload));
  }

  private void send(String exchange, String routingKey, Message message) {
    boolean returned;
    try {
      returned = rabbitOperations.invoke(ops -> sendAndConfirm(ops, exchange, routingKey, message));
    } catch (AmqpException | ShutdownSignalException e) {
      throw new MessagePublishException(e);
    }
    if (returned) {
      throw new MessagePublishException(new UnroutableMessageException());
    }
  }

  // The listener sits on the raw channel, so the broker's basic.return is handled on the
  // connection thread before the confirm that follows it releases waitForConfirmsOrDie.
  private boolean sendAndConfirm(
      RabbitOperations ops, String exchange, String routingKey, Message message) {
    ReturnTracker tracker = new ReturnTracker();
    Channel channel = ops.execute(target -> addListener(target, tracker));
    try {
      ops.send(exchange, routingKey, message);
      ops.waitForConfirmsOrDie(confirmTimeout.toMillis());
    } catch (RuntimeException e) {
      removeListener(channel, tracker, e);
      throw e;
    }
    removeListener(channel, tracker, null);
    return tracker.isReturned();
  }

  // A channel closed by the broker can refuse the removal; the listener dies with the channel, so
  // the failure is attached to the publish failure, if any, and otherwise ignored.
  private static void removeListener(Channel channel, ReturnTracker tracker, Exception failure) {
    try {
      channel.removeReturnListener(tracker);
    } catch (RuntimeException e) {
      if (failure != null) {
        failure.addSuppressed(e);
      } else {
        log.debug("Return listener not removed: {}", e.getClass().getName());
      }
    }
  }

  private static Channel addListener(Channel channel, ReturnTracker tracker) {
    channel.addReturnListener(tracker);
    return channel;
  }

  /**
   * Builds an AMQP {@link Message} with {@code contentType=application/json}, {@code
   * contentEncoding=UTF-8} and persistent delivery mode. The payload is encoded as UTF-8 bytes; no
   * further serialization is applied.
   *
   * @param payload the JSON payload to wrap
   * @return the AMQP message ready to send
   */
  private static Message jsonMessage(String payload) {
    MessageProperties props = new MessageProperties();
    props.setContentType(CONTENT_TYPE);
    props.setContentEncoding(StandardCharsets.UTF_8.name());
    props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    return new Message(payload.getBytes(StandardCharsets.UTF_8), props);
  }
}
