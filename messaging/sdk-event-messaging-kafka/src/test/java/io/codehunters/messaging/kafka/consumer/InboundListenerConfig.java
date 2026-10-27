package io.codehunters.messaging.kafka.consumer;

import java.time.Duration;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;

/**
 * An application of the inbound ITs: one listener on {@code it.topic} whose behaviour is a bean,
 * and the SDK retry topology for that topic with short delays and {@link IllegalStateException}
 * non-retryable.
 */
@Configuration(proxyBeanMethods = false)
class InboundListenerConfig {

  static final List<Duration> DELAYS =
      List.of(Duration.ofMillis(200), Duration.ofMillis(200), Duration.ofMillis(200));

  /** What the listener does with each record it receives. */
  @FunctionalInterface
  interface Behaviour {
    void on(ConsumerRecord<String, String> record);
  }

  @Bean
  RetryTopicConfiguration inboundRetry(
      InboundRetryTopics topics, @Value("${it.topic}") String topic) {
    return topics.configuration(
        new InboundRetry(topic, DELAYS, List.of(IllegalStateException.class)));
  }

  @Bean
  InboundListener inboundListener(Behaviour behaviour) {
    return new InboundListener(behaviour);
  }

  /** The listener; delegates to the {@link Behaviour} bean. */
  public static class InboundListener {

    private final Behaviour behaviour;

    InboundListener(Behaviour behaviour) {
      this.behaviour = behaviour;
    }

    /**
     * Receives a record of the main topic or of one of its retry topics.
     *
     * @param record the record
     */
    @KafkaListener(topics = "${it.topic}", groupId = "${it.group}")
    public void on(ConsumerRecord<String, String> record) {
      behaviour.on(record);
    }
  }
}
