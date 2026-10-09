package io.codehunters.messaging;

/**
 * Outbound port to publish a serialized message to a broker topic/exchange.
 *
 * <p><b>Contract:</b> Implementations MUST set the broker {@code contentType} to {@code
 * application/json} and {@code contentEncoding} to {@code UTF-8}. Payloads MUST be valid JSON
 * serialized as UTF-8. Non-JSON payloads are not supported by this port; callers needing other
 * formats must introduce a new port.
 */
public interface MessagePublisherPort {

  /**
   * Broker header carrying the outbox row id as UTF-8 text. Adapters set it in {@link
   * #publish(OutboxMessage)}; delivery is at-least-once, so consumers dedupe on it.
   */
  String OUTBOX_ID_HEADER = "outbox-message-id";

  /**
   * W3C trace context header. Adapters set it in {@link #publish(OutboxMessage)} to the message's
   * stored {@link OutboxMessage} {@code traceparent}, when it has one.
   */
  String TRACEPARENT_HEADER = "traceparent";

  /**
   * Copy of {@link #TRACEPARENT_HEADER} for consumers that read only this one. Adapters set it only
   * when {@code codehunters.outbox.publisher.legacy-traceparent-header} is {@code true}.
   */
  String LEGACY_TRACEPARENT_HEADER = "x-traceparent";

  /**
   * Publishes a JSON message with a routing key.
   *
   * @param topic the destination topic or exchange
   * @param routingKey the routing key
   * @param payload the message payload, MUST be valid JSON (UTF-8)
   */
  void publish(String topic, String routingKey, String payload);

  /**
   * Publishes a JSON message without a routing key.
   *
   * @param topic the destination topic or exchange
   * @param payload the message payload, MUST be valid JSON (UTF-8)
   */
  void publish(String topic, String payload);

  /**
   * Publishes a whole outbox message. Adapters that support a partition key override this to read
   * the message key and id; the default ignores the key and id and delegates to {@link
   * #publish(String, String, String)} when the routing key is non-blank, otherwise to {@link
   * #publish(String, String)}.
   *
   * @param message the claimed outbox message carrying topic, routing key, key, id and payload
   */
  default void publish(OutboxMessage<String> message) {
    String routingKey = message.getRoutingKey();
    if (routingKey != null && !routingKey.isBlank()) {
      publish(message.getTopic(), routingKey, message.getMessage());
    } else {
      publish(message.getTopic(), message.getMessage());
    }
  }
}
