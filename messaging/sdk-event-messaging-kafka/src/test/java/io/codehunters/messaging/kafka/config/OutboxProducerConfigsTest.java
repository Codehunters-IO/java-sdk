package io.codehunters.messaging.kafka.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

class OutboxProducerConfigsTest {

  private static final String BOOTSTRAP = "broker:9092";

  private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);
  private static final String APP_NAME = "orders-service";
  private static final String MAX_IN_FLIGHT = ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION;
  private static final String RETRIES = ProducerConfig.RETRIES_CONFIG;
  private static final int SEND_TIMEOUT_MS = 5000;

  private static final Map<String, Object> LOCAL_WITH_TIMEOUTS =
      Map.of(
          ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
          "localhost:1",
          ProducerConfig.LINGER_MS_CONFIG,
          100,
          ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
          30000,
          ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
          120000);

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
    Map<String, Object> config = OutboxProducerConfigs.from(APPLICATION, SEND_TIMEOUT, APP_NAME);

    assertThat(config).containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
  }

  @Test
  void should_suffix_application_client_id_when_application_sets_one() {
    Map<String, Object> config = OutboxProducerConfigs.from(APPLICATION, SEND_TIMEOUT, APP_NAME);

    assertThat(config).containsEntry(ProducerConfig.CLIENT_ID_CONFIG, "orders-outbox");
  }

  @Test
  void should_name_client_after_application_when_client_id_unset() {
    Map<String, Object> config =
        OutboxProducerConfigs.from(
            withSetting(ProducerConfig.CLIENT_ID_CONFIG, " "), SEND_TIMEOUT, APP_NAME);

    assertThat(config).containsEntry(ProducerConfig.CLIENT_ID_CONFIG, "outbox-orders-service");
  }

  @Test
  void should_force_string_serializers_when_application_uses_json() {
    Map<String, Object> config = OutboxProducerConfigs.from(APPLICATION, SEND_TIMEOUT, APP_NAME);

    assertThat(config)
        .containsEntry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class)
        .containsEntry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
  }

  @Test
  void should_force_idempotent_all_acks_producer_when_application_relaxes_them() {
    Map<String, Object> config = OutboxProducerConfigs.from(APPLICATION, SEND_TIMEOUT, APP_NAME);

    assertThat(config)
        .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
        .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
  }

  @Test
  void should_bound_metadata_and_buffer_wait_by_send_timeout_when_deriving_config() {
    Map<String, Object> config = OutboxProducerConfigs.from(APPLICATION, SEND_TIMEOUT, APP_NAME);

    assertThat(config).containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, SEND_TIMEOUT_MS);
  }

  @Test
  void should_bound_delivery_by_send_timeout_without_lingering_when_deriving_config() {
    Map<String, Object> config = OutboxProducerConfigs.from(APPLICATION, SEND_TIMEOUT, APP_NAME);

    assertThat(config)
        .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, SEND_TIMEOUT_MS)
        .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, SEND_TIMEOUT_MS)
        .containsEntry(ProducerConfig.LINGER_MS_CONFIG, 0);
  }

  @Test
  void should_build_kafka_producer_when_application_sets_conflicting_timeouts() {
    Map<String, Object> config =
        OutboxProducerConfigs.from(LOCAL_WITH_TIMEOUTS, SEND_TIMEOUT, APP_NAME);

    try (var producer = new KafkaProducer<String, String>(config)) {
      assertThat(producer.metrics()).isNotEmpty();
      producer.close(Duration.ZERO);
    }
  }

  @Test
  void should_cap_max_in_flight_at_five_when_application_allows_more() {
    Map<String, Object> config =
        OutboxProducerConfigs.from(withSetting(MAX_IN_FLIGHT, 10), SEND_TIMEOUT, APP_NAME);

    assertThat(config).containsEntry(MAX_IN_FLIGHT, 5);
  }

  @Test
  void should_keep_max_in_flight_when_application_allows_at_most_five() {
    Map<String, Object> config =
        OutboxProducerConfigs.from(withSetting(MAX_IN_FLIGHT, "3"), SEND_TIMEOUT, APP_NAME);

    assertThat(config).containsEntry(MAX_IN_FLIGHT, 3);
  }

  @Test
  void should_restore_retries_when_application_disables_them() {
    Map<String, Object> config =
        OutboxProducerConfigs.from(withSetting(RETRIES, "0"), SEND_TIMEOUT, APP_NAME);

    assertThat(config).containsEntry(RETRIES, Integer.MAX_VALUE);
  }

  @Test
  void should_keep_retries_when_application_sets_a_positive_count() {
    Map<String, Object> config =
        OutboxProducerConfigs.from(withSetting(RETRIES, 7), SEND_TIMEOUT, APP_NAME);

    assertThat(config).containsEntry(RETRIES, 7);
  }

  @Test
  void should_leave_retries_and_in_flight_to_kafka_defaults_when_application_sets_none() {
    Map<String, Object> config = OutboxProducerConfigs.from(APPLICATION, SEND_TIMEOUT, APP_NAME);

    assertThat(config).doesNotContainKeys(RETRIES, MAX_IN_FLIGHT);
  }

  @Test
  void should_drop_transactional_id_when_application_producer_is_transactional() {
    Map<String, Object> config = OutboxProducerConfigs.from(APPLICATION, SEND_TIMEOUT, APP_NAME);

    assertThat(config).doesNotContainKey(ProducerConfig.TRANSACTIONAL_ID_CONFIG);
  }

  private static Map<String, Object> withSetting(String key, Object value) {
    Map<String, Object> application = new HashMap<>(APPLICATION);
    application.put(key, value);
    return application;
  }
}
