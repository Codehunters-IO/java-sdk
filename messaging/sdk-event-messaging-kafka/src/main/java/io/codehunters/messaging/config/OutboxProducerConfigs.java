package io.codehunters.messaging.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * Derives the outbox producer configuration from the application's. Connection, security and tuning
 * settings are kept; serializers, acknowledgements and idempotence are forced, and the
 * transactional id is dropped because outbox sends run outside Kafka transactions.
 */
final class OutboxProducerConfigs {

  private OutboxProducerConfigs() {}

  static Map<String, Object> from(Map<String, Object> application) {
    Map<String, Object> config = new HashMap<>(application);
    config.remove(ProducerConfig.TRANSACTIONAL_ID_CONFIG);
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    config.put(ProducerConfig.ACKS_CONFIG, "all");
    config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
    return Map.copyOf(config);
  }
}
