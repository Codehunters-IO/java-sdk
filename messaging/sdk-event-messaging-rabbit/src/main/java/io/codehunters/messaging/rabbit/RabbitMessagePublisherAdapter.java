package io.codehunters.messaging.rabbit;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import io.codehunters.messaging.MessagePublishException;
import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.W3cTraceParent;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * mandatory=true} on a connection factory with simple or correlated publisher confirms and returns.
 * A nack, a confirm timeout, a closed channel (for example a missing exchange) or a {@code
 * basic.return} (no queue bound for the routing key) surfaces as {@link MessagePublishException},
 * whose message never carries broker text, so the dispatcher retries the row.
 *
 * <p>{@link #publish(OutboxMessage)} sets the AMQP {@code messageId} and the {@value
 * MessagePublisherPort#OUTBOX_ID_HEADER} header to the outbox row id; consumers MUST dedupe on it.
 * It also sets the {@value MessagePublisherPort#TRACEPARENT_HEADER} header to the trace context
 * stored with the row, so the consumer continues the trace of the work that created it rather than
 * the dispatcher's; {@value MessagePublisherPort#LEGACY_TRACEPARENT_HEADER} only when asked to. A
 * stored value that is not a valid {@link W3cTraceParent} is not sent. The dispatcher publishes a
 * claimed batch sequentially, so deployments MUST keep {@code batchSize × confirmTimeout <
 * claim.ttlMs}.
 */
public class RabbitMessagePublisherAdapter implements MessagePublisherPort {

  private static final Logger log = LoggerFactory.getLogger(RabbitMessagePublisherAdapter.class);

  /**
   * Pinned content type for every message produced by this adapter. The SDK serializes payloads
   * exclusively as JSON; this value resolves to the literal {@code "application/json"} and MUST NOT
   * be overridden per call.
   */
  private static final String CONTENT_TYPE = MessageProperties.CONTENT_TYPE_JSON;

  private final RabbitOperations rabbitOperations;
  private final Duration confirmTimeout;
  private final boolean legacyTraceparentHeader;

  /**
   * Creates the adapter, sending the stored trace context as {@code traceparent} only.
   *
   * @param rabbitOperations the mandatory, confirming template that sends the messages
   * @param confirmTimeout the maximum wait for the broker confirm of one message
   */
  public RabbitMessagePublisherAdapter(RabbitOperations rabbitOperations, Duration confirmTimeout) {
    this(rabbitOperations, confirmTimeout, false);
  }

  /**
   * Creates the adapter.
   *
   * @param rabbitOperations the mandatory, confirming template that sends the messages
   * @param confirmTimeout the maximum wait for the broker confirm of one message
   * @param legacyTraceparentHeader whether the stored trace context is also sent as {@value
   *     MessagePublisherPort#LEGACY_TRACEPARENT_HEADER}
   */
  public RabbitMessagePublisherAdapter(
      RabbitOperations rabbitOperations, Duration confirmTimeout, boolean legacyTraceparentHeader) {
    this.rabbitOperations = rabbitOperations;
    this.confirmTimeout = confirmTimeout;
    this.legacyTraceparentHeader = legacyTraceparentHeader;
  }

  /**
   * Sends the message with its routing key (empty when unset), tagged with the outbox row id as
   * AMQP {@code messageId} and as the {@value MessagePublisherPort#OUTBOX_ID_HEADER} header, and
   * with its stored trace context, when it has one, as the {@value
   * MessagePublisherPort#TRACEPARENT_HEADER} header.
   *
   * @param message the claimed outbox message
   */
  @Override
  public void publish(OutboxMessage<String> message) {
    String id = message.id().toString();
    Message amqpMessage = jsonMessage(message.message());
    amqpMessage.getMessageProperties().setMessageId(id);
    amqpMessage.getMessageProperties().setHeader(OUTBOX_ID_HEADER, id);
    setTraceHeaders(amqpMessage.getMessageProperties(), message.traceparent());
    String routingKey = message.routingKey() == null ? "" : message.routingKey();
    log.debug("Publishing outbox message to exchange: {}, id: {}", message.topic(), id);
    send(message.topic(), routingKey, amqpMessage);
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

  // The stored context is sent as it is: the consumer's span then parents on the span that created
  // the row, not on the scheduled dispatch that happens to publish it. An invalid one is not sent.
  private void setTraceHeaders(MessageProperties properties, String traceparent) {
    if (!W3cTraceParent.isValid(traceparent)) {
      return;
    }
    properties.setHeader(TRACEPARENT_HEADER, traceparent);
    if (legacyTraceparentHeader) {
      properties.setHeader(LEGACY_TRACEPARENT_HEADER, traceparent);
    }
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
