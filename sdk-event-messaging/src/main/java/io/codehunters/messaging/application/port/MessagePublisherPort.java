package io.codehunters.messaging.application.port;

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
}
