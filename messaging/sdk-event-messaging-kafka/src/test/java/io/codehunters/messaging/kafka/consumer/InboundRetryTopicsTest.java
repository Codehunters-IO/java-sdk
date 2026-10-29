package io.codehunters.messaging.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.retrytopic.DestinationTopic;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;

class InboundRetryTopicsTest {

  private static final String TOPIC = "orders.in";
  private static final List<Duration> PRODUCTION_DELAYS =
      List.of(Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofMinutes(5));
  private static final List<Duration> SHORT_DELAYS =
      List.of(Duration.ofMillis(200), Duration.ofMillis(200), Duration.ofMillis(200));
  private static final List<String> SUFFIXES =
      List.of("", "-retry-0", "-retry-1", "-retry-2", ".DLT");
  private static final int FIRST_ATTEMPT = 1;

  @SuppressWarnings("unchecked")
  private final InboundRetryTopics topics = new InboundRetryTopics(mock(KafkaOperations.class));

  static class SpecificStateException extends IllegalStateException {}

  @Test
  void should_name_retry_and_dlt_topics_by_index_when_delays_differ() {
    RetryTopicConfiguration configuration = topics.configuration(retry(PRODUCTION_DELAYS));

    assertThat(configuration.getDestinationTopicProperties())
        .extracting(DestinationTopic.Properties::suffix)
        .containsExactlyElementsOf(SUFFIXES);
  }

  @Test
  void should_delay_each_hop_exactly_as_configured_when_delays_differ() {
    RetryTopicConfiguration configuration = topics.configuration(retry(PRODUCTION_DELAYS));

    assertThat(configuration.getDestinationTopicProperties())
        .extracting(DestinationTopic.Properties::delay)
        .containsExactly(0L, 10_000L, 60_000L, 300_000L, 0L);
  }

  @Test
  void should_name_topics_by_index_when_delays_are_equal() {
    RetryTopicConfiguration configuration = topics.configuration(retry(SHORT_DELAYS));

    assertThat(configuration.getDestinationTopicProperties())
        .extracting(DestinationTopic.Properties::suffix)
        .containsExactlyElementsOf(SUFFIXES);
  }

  @Test
  void should_not_create_topics_when_configured() {
    RetryTopicConfiguration configuration = topics.configuration(retry(PRODUCTION_DELAYS));

    assertThat((Object) configuration.forKafkaTopicAutoCreation())
        .extracting("shouldCreateTopics")
        .isEqualTo(false);
  }

  @Test
  void should_not_start_dlt_handler_when_configured() {
    RetryTopicConfiguration configuration = topics.configuration(retry(PRODUCTION_DELAYS));

    assertThat(configuration.getDestinationTopicProperties().getLast().autoStartDltHandler())
        .isFalse();
  }

  @Test
  void should_apply_only_to_its_topic_when_configured() {
    RetryTopicConfiguration configuration = topics.configuration(retry(PRODUCTION_DELAYS));

    assertThat(configuration.hasConfigurationForTopics(new String[] {TOPIC})).isTrue();
    assertThat(configuration.hasConfigurationForTopics(new String[] {"payments.in"})).isFalse();
  }

  @Test
  void should_not_retry_unreadable_envelope_when_not_listed() {
    assertThat(mainTopic().shouldRetryOn(FIRST_ATTEMPT, new UnreadableEnvelopeException("x")))
        .isFalse();
  }

  @Test
  void should_not_retry_listed_exception_when_thrown() {
    assertThat(mainTopic().shouldRetryOn(FIRST_ATTEMPT, new IllegalStateException())).isFalse();
  }

  @Test
  void should_not_retry_subclass_of_listed_exception_when_thrown() {
    assertThat(mainTopic().shouldRetryOn(FIRST_ATTEMPT, new SpecificStateException())).isFalse();
  }

  @Test
  void should_not_retry_listed_exception_when_wrapped_as_cause() {
    assertThat(
            mainTopic()
                .shouldRetryOn(FIRST_ATTEMPT, new RuntimeException(new IllegalStateException())))
        .isFalse();
  }

  @Test
  void should_retry_unclassified_exception_when_thrown() {
    assertThat(mainTopic().shouldRetryOn(FIRST_ATTEMPT, new RuntimeException())).isTrue();
  }

  @Test
  void should_reject_blank_topic_when_created() {
    assertThatThrownBy(() -> new InboundRetry(" ", PRODUCTION_DELAYS, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_empty_delays_when_created() {
    assertThatThrownBy(() -> new InboundRetry(TOPIC, List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_zero_delay_when_created() {
    assertThatThrownBy(() -> new InboundRetry(TOPIC, List.of(Duration.ZERO), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_negative_delay_when_created() {
    assertThatThrownBy(() -> new InboundRetry(TOPIC, List.of(Duration.ofSeconds(-1)), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_keep_retry_lists_unmodifiable_when_created() {
    InboundRetry retry = retry(PRODUCTION_DELAYS);

    assertThatThrownBy(() -> retry.delays().add(Duration.ZERO))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> retry.notRetryOn().add(RuntimeException.class))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private DestinationTopic mainTopic() {
    DestinationTopic.Properties main =
        topics.configuration(retry(PRODUCTION_DELAYS)).getDestinationTopicProperties().getFirst();
    return new DestinationTopic(TOPIC, main);
  }

  private static InboundRetry retry(List<Duration> delays) {
    return new InboundRetry(TOPIC, delays, List.of(IllegalStateException.class));
  }
}
