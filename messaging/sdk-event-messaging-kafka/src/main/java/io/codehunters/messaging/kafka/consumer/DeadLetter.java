package io.codehunters.messaging.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * A record about to be published to its dead-letter topic.
 *
 * @param record the failed record, as consumed
 * @param failure the listener's own exception, unwrapped from Spring Kafka's wrappers
 */
public record DeadLetter(ConsumerRecord<?, ?> record, Throwable failure) {

  /**
   * Prints the record position and the failure class only, never the record value, its headers or
   * the exception message.
   *
   * @return {@code DeadLetter[<topic>-<partition>@<offset>, <failure class>]}
   */
  @Override
  public String toString() {
    return "DeadLetter["
        + record.topic()
        + "-"
        + record.partition()
        + "@"
        + record.offset()
        + ", "
        + failure.getClass().getName()
        + "]";
  }
}
