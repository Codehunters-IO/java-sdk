package io.codehunters.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

class OutboxProducerConfigsTest {

  private static final String BOOTSTRAP = "broker:9092";

  private static final Map<String, Object> APPLICATION =
      Map.of(
          ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
          BOOTSTRAP,
          ProducerConfig.CLIENT_ID_CONFIG,
          "orders",
          ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
          JacksonJsonSerializer.class,
          ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
          JacksonJsonSerializer.class,
          ProducerConfig.ACKS_CONFIG,
          "1",
          ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
          false,
          ProducerConfig.TRANSACTIONAL_ID_CONFIG,
          "orders-tx-");

  @Test
  void should_keep_application_connection_settings_when_deriving_outbox_config() {
    Map<String, Object> config = OutboxProducerConfigs.from(APPLICATION);

    assertThat(config)
        .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP)
        .containsEntry(ProducerConfig.CLIENT_ID_CONFIG, "orders");
  }

  @Test
  void should_force_string_serializers_when_application_uses_json() {
    Map<String, Object> config = OutboxProducerConfigs.from(APPLICATION);

    assertThat(config)
        .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
        .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
  }

  @Test
  void should_force_idempotent_all_acks_producer_when_application_relaxes_them() {
    Map<String, Object> config = OutboxProducerConfigs.from(APPLICATION);

    assertThat(config)
        .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
        .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
  }

  @Test
  void should_drop_transactional_id_when_application_producer_is_transactional() {
    Map<String, Object> config = OutboxProducerConfigs.from(APPLICATION);

    assertThat(config).doesNotContainKey(ProducerConfig.TRANSACTIONAL_ID_CONFIG);
  }
}
