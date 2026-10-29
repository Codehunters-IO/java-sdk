package io.codehunters.messaging.kafka.consumer.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Derives the producer that forwards failed inbound records to their retry and dead-letter topics
 * from the application's producer configuration. Connection, security and tuning settings are kept;
 * String serializers, {@code acks=all} and idempotence are forced so the value is forwarded byte
 * for byte and never duplicated by a producer retry, and the transactional id is dropped because
 * forwarding runs outside Kafka transactions. The client id is the application's with an {@code
 * -inbound-retry} suffix, or {@code inbound-retry-<spring.application.name>}.
 *
 * <p>Idempotence rejects {@code max.in.flight.requests.per.connection} above 5 and {@code
 * retries=0}; an application value above 5 is capped and {@code retries=0} is reset to Kafka's
 * default.
 */
final class ForwardingProducerConfigs {

  private static final int IDEMPOTENT_MAX_IN_FLIGHT = 5;

  private ForwardingProducerConfigs() {}

  static Map<String, Object> from(Map<String, Object> application, String applicationName) {
    Map<String, Object> config = new HashMap<>(application);
    config.remove(ProducerConfig.TRANSACTIONAL_ID_CONFIG);
    config.put(ProducerConfig.CLIENT_ID_CONFIG, clientId(application, applicationName));
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.ACKS_CONFIG, "all");
    config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    config.computeIfPresent(
        ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
        (key, value) -> Math.min(intValue(value), IDEMPOTENT_MAX_IN_FLIGHT));
    config.computeIfPresent(
        ProducerConfig.RETRIES_CONFIG,
        (key, value) -> intValue(value) > 0 ? intValue(value) : Integer.MAX_VALUE);
    return Map.copyOf(config);
  }

  private static String clientId(Map<String, Object> application, String applicationName) {
    Object clientId = application.get(ProducerConfig.CLIENT_ID_CONFIG);
    return clientId == null || clientId.toString().isBlank()
        ? "inbound-retry-" + applicationName
        : clientId.toString().trim() + "-inbound-retry";
  }

  private static int intValue(Object value) {
    return value instanceof Number number
        ? number.intValue()
        : Integer.parseInt(value.toString().trim());
  }
}
