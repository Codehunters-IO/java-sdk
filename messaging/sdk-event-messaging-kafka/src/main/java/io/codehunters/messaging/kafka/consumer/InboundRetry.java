package io.codehunters.messaging.kafka.consumer;

import java.time.Duration;
import java.util.List;

/**
 * The retry policy of one inbound topic: the delay before each retry and the exceptions that go
 * straight to the dead-letter topic. A record gets {@code delays.size() + 1} attempts: the first on
 * the main topic, then one on each {@code <topic>-retry-<n>} topic, after which it is published to
 * {@code <topic>.DLT}.
 *
 * <p>The retry and dead-letter topics are not created by the SDK. They must exist before the
 * listener starts, with at least the main topic's partition count, because a failed record is
 * republished on its own partition number.
 *
 * @param topic the main topic
 * @param delays the delay before each retry, in order; one retry topic per delay
 * @param notRetryOn the exceptions (and their subclasses, also when found as a cause) that are not
 *     retried; {@link UnreadableEnvelopeException} is always added by {@link InboundRetryTopics}
 */
public record InboundRetry(
    String topic, List<Duration> delays, List<Class<? extends Throwable>> notRetryOn) {

  /**
   * Validates the policy and copies the lists.
   *
   * @throws IllegalArgumentException when the topic is blank, or the delays are empty or not all
   *     positive
   */
  public InboundRetry {
    if (topic == null || topic.isBlank()) {
      throw new IllegalArgumentException("topic must not be blank");
    }
    if (delays == null || delays.isEmpty()) {
      throw new IllegalArgumentException("delays must not be empty");
    }
    delays = List.copyOf(delays);
    if (delays.stream().anyMatch(delay -> delay.isZero() || delay.isNegative())) {
      throw new IllegalArgumentException("delays must be positive");
    }
    notRetryOn = notRetryOn == null ? List.of() : List.copyOf(notRetryOn);
  }
}
