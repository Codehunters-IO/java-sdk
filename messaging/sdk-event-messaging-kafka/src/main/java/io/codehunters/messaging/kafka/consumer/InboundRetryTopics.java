package io.codehunters.messaging.kafka.consumer;

import java.util.ArrayList;
import java.util.List;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;

/**
 * Builds the non-blocking retry topology of an inbound topic. A record whose listener fails is
 * republished to {@code <topic>-retry-0}, {@code -retry-1}, ... with the configured delays and
 * finally to {@code <topic>.DLT}, so a failing record never stalls its partition: the main topic
 * keeps being consumed while the record waits on its retry topic.
 *
 * <p>Exceptions listed in {@link InboundRetry#notRetryOn()}, their subclasses, those found as a
 * cause, and {@link UnreadableEnvelopeException} go straight to the dead-letter topic. Any other
 * exception is retried. Topics are never created here: they are provisioned outside the service,
 * all with the main topic's partition count. Nothing consumes the dead-letter topic.
 *
 * <p>Topics are suffixed by index, one per delay even when delays are equal, so the topic names do
 * not depend on the delay values.
 *
 * <p>Expose the result as a bean; Spring Kafka applies it to the {@code @KafkaListener} of that
 * topic.
 */
public final class InboundRetryTopics {

  /** Suffix of the retry topics, followed by {@code -<index>}. */
  public static final String RETRY_SUFFIX = "-retry";

  /** Suffix of the dead-letter topic. */
  public static final String DLT_SUFFIX = ".DLT";

  private final KafkaOperations<String, String> forwardingTemplate;

  /**
   * Creates the factory.
   *
   * @param forwardingTemplate the template failed records are republished with; String key and
   *     value serializers, so the value is forwarded byte for byte
   */
  public InboundRetryTopics(KafkaOperations<String, String> forwardingTemplate) {
    this.forwardingTemplate = forwardingTemplate;
  }

  /**
   * Builds the retry configuration of one topic.
   *
   * @param retry the topic's retry policy
   * @return the configuration, applying to {@link InboundRetry#topic()} only
   */
  public RetryTopicConfiguration configuration(InboundRetry retry) {
    return RetryTopicConfigurationBuilder.newInstance()
        .includeTopic(retry.topic())
        .maxAttempts(retry.delays().size() + 1)
        .customBackoff(new ListBackOff(retry.delays()))
        .suffixTopicsWithIndexValues()
        .sameIntervalTopicReuseStrategy(SameIntervalTopicReuseStrategy.MULTIPLE_TOPICS)
        .retryTopicSuffix(RETRY_SUFFIX)
        .dltSuffix(DLT_SUFFIX)
        .doNotAutoCreateRetryTopics()
        .autoStartDltHandler(false)
        .notRetryOn(notRetryOn(retry))
        .traversingCauses()
        .create(forwardingTemplate);
  }

  private static List<Class<? extends Throwable>> notRetryOn(InboundRetry retry) {
    List<Class<? extends Throwable>> classes = new ArrayList<>(retry.notRetryOn());
    classes.add(UnreadableEnvelopeException.class);
    return List.copyOf(classes);
  }
}
