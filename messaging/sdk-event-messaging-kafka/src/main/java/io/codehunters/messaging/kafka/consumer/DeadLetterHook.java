package io.codehunters.messaging.kafka.consumer;

/**
 * Called once per record before it is published to its dead-letter topic ({@code <topic>.DLT}), on
 * the consumer thread, after its last attempt failed or right away when its exception is not
 * retryable. It is never called for a hop to a retry topic. Every {@code DeadLetterHook} bean is
 * called, in bean order.
 *
 * <p>A hook that throws fails the recovery: nothing is published and the record is redelivered at
 * the same hop, so no record reaches the dead-letter topic without its hooks having run. Delivery
 * is at least once: a hook can see the same record again after a failure, so its side effects must
 * tolerate duplicates.
 *
 * <p>The failure is the listener's own exception; its message may carry personal data, so a hook
 * should keep its class name only.
 */
@FunctionalInterface
public interface DeadLetterHook {

  /** Header carrying the envelope's event id on retry and dead-letter records. */
  String EVENT_ID_HEADER = "event-id";

  /** Header carrying the W3C trace id of the record's {@code traceparent}. */
  String TRACE_ID_HEADER = "trace-id";

  /**
   * Runs before the record is published to its dead-letter topic.
   *
   * @param deadLetter the record and the listener's exception
   */
  void beforeDeadLetter(DeadLetter deadLetter);
}
