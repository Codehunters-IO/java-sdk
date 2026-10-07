package io.codehunters.messaging.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Derives the outbox producer configuration from the application's. Connection, security and tuning
 * settings are kept; serializers, acknowledgements and idempotence are forced, and the
 * transactional id is dropped because outbox sends run outside Kafka transactions. The client id is
 * the application's with an {@code -outbox} suffix, or {@code outbox-<spring.application.name>}
 * when the application sets none.
 *
 * <p>Every wait of one send is bounded by the outbox send timeout: {@code max.block.ms} (metadata
 * fetch, full buffer), {@code delivery.timeout.ms} and {@code request.timeout.ms} all equal it, and
 * {@code linger.ms} is {@code 0}, since the dispatcher sends one record at a time and waits for its
 * acknowledgement, so lingering only adds latency. Kafka requires {@code delivery.timeout.ms >=
 * linger.ms + request.timeout.ms}, which these values meet for any send timeout.
 *
 * <p>Idempotence rejects {@code max.in.flight.requests.per.connection} above 5 and {@code
 * retries=0}; an application value above 5 is capped at 5 and {@code retries=0} is reset to {@link
 * Integer#MAX_VALUE} (Kafka's default, bounded by {@code delivery.timeout.ms}), so a setting meant
 * for the application's producer cannot make every outbox send fail.
 */
final class OutboxProducerConfigs {

  private static final int IDEMPOTENT_MAX_IN_FLIGHT = 5;

  private OutboxProducerConfigs() {}

  static Map<String, Object> from(
      Map<String, Object> application, Duration sendTimeout, String applicationName) {
    Map<String, Object> config = new HashMap<>(application);
    config.remove(ProducerConfig.TRANSACTIONAL_ID_CONFIG);
    config.put(ProducerConfig.CLIENT_ID_CONFIG, outboxClientId(application, applicationName));
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.ACKS_CONFIG, "all");
    config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    boundBySendTimeout(config, Math.toIntExact(sendTimeout.toMillis()));
    keepIdempotenceValid(config);
    return Map.copyOf(config);
  }

  /**
   * Parses the configuration as the producer will, so an invalid value fails startup rather than
   * the first dispatch.
   *
   * @throws org.apache.kafka.common.config.ConfigException naming the invalid setting
   */
  static Map<String, Object> validated(Map<String, Object> config) {
    new ProducerConfig(config);
    return config;
  }

  // A client id of its own keeps the outbox producer's JMX names and broker quotas apart from the
  // application's producer.
  private static String outboxClientId(Map<String, Object> application, String applicationName) {
    Object clientId = application.get(ProducerConfig.CLIENT_ID_CONFIG);
    return clientId == null || clientId.toString().isBlank()
        ? "outbox-" + applicationName
        : clientId.toString().trim() + "-outbox";
  }

  private static void keepIdempotenceValid(Map<String, Object> config) {
    config.computeIfPresent(
        ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
        (key, value) -> Math.min(intValue(value), IDEMPOTENT_MAX_IN_FLIGHT));
    config.computeIfPresent(
        ProducerConfig.RETRIES_CONFIG,
        (key, value) -> intValue(value) > 0 ? intValue(value) : Integer.MAX_VALUE);
  }

  private static int intValue(Object value) {
    return value instanceof Number number
        ? number.intValue()
        : Integer.parseInt(value.toString().trim());
  }

  private static void boundBySendTimeout(Map<String, Object> config, int sendTimeoutMs) {
    config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, sendTimeoutMs);
    config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, sendTimeoutMs);
    config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, sendTimeoutMs);
    config.put(ProducerConfig.LINGER_MS_CONFIG, 0);
  }
}
