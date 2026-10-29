package io.codehunters.messaging.kafka.consumer.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;

class ForwardingProducerConfigsTest {

  private static final String APP = "ocr";

  @Test
  void should_suffix_application_client_id_when_application_sets_one() {
    Map<String, Object> config =
        ForwardingProducerConfigs.from(Map.of(ProducerConfig.CLIENT_ID_CONFIG, " ocr-1 "), APP);

    assertThat(config).containsEntry(ProducerConfig.CLIENT_ID_CONFIG, "ocr-1-inbound-retry");
  }

  @Test
  void should_name_client_after_application_when_application_sets_no_client_id() {
    Map<String, Object> config = ForwardingProducerConfigs.from(Map.of(), APP);

    assertThat(config).containsEntry(ProducerConfig.CLIENT_ID_CONFIG, "inbound-retry-ocr");
  }

  @Test
  void should_cap_in_flight_requests_when_application_allows_more_than_idempotence() {
    Map<String, Object> config =
        ForwardingProducerConfigs.from(
            Map.of(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "10"), APP);

    assertThat(config).containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
  }

  @Test
  void should_reset_retries_when_application_disables_them() {
    Map<String, Object> config =
        ForwardingProducerConfigs.from(Map.of(ProducerConfig.RETRIES_CONFIG, 0), APP);

    assertThat(config).containsEntry(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
  }

  @Test
  void should_keep_retries_when_application_sets_positive_value() {
    Map<String, Object> config =
        ForwardingProducerConfigs.from(Map.of(ProducerConfig.RETRIES_CONFIG, 3), APP);

    assertThat(config).containsEntry(ProducerConfig.RETRIES_CONFIG, 3);
  }
}
