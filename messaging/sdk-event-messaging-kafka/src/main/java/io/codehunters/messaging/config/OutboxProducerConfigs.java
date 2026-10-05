package io.codehunters.messaging.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Derives the outbox producer configuration from the application's. Connection, security and tuning
 * settings are kept; serializers, acknowledgements and idempotence are forced, and the
 * transactional id is dropped because outbox sends run outside Kafka transactions.
 *
 * <p>Every wait of one send is bounded by the outbox send timeout: {@code max.block.ms} (metadata
 * fetch, full buffer), {@code delivery.timeout.ms} and {@code request.timeout.ms} all equal it, and
 * {@code linger.ms} is {@code 0}, since the dispatcher sends one record at a time and waits for its
 * acknowledgement, so lingering only adds latency. Kafka requires {@code delivery.timeout.ms >=
 * linger.ms + request.timeout.ms}, which these values meet for any send timeout.
 */
final class OutboxProducerConfigs {

  private OutboxProducerConfigs() {}

  static Map<String, Object> from(Map<String, Object> application, Duration sendTimeout) {
    Map<String, Object> config = new HashMap<>(application);
    config.remove(ProducerConfig.TRANSACTIONAL_ID_CONFIG);
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.ACKS_CONFIG, "all");
    config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    boundBySendTimeout(config, Math.toIntExact(sendTimeout.toMillis()));
    return Map.copyOf(config);
  }

  private static void boundBySendTimeout(Map<String, Object> config, int sendTimeoutMs) {
    config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, sendTimeoutMs);
    config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, sendTimeoutMs);
    config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, sendTimeoutMs);
    config.put(ProducerConfig.LINGER_MS_CONFIG, 0);
  }
}
