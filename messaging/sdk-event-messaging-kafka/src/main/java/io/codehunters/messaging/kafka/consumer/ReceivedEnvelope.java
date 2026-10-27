package io.codehunters.messaging.kafka.consumer;

import io.codehunters.messaging.EventMessage;

/**
 * An event envelope read from a Kafka record, with the record headers consumers dedupe, trace and
 * retry on.
 *
 * @param message the envelope
 * @param outboxMessageId the {@code outbox-message-id} record header, or null
 * @param traceparent the {@code traceparent} record header, or null
 * @param attempt the delivery attempt, 1 for the first; read from the retry-topic header
 * @param <T> the payload type
 */
public record ReceivedEnvelope<T>(
    EventMessage<T> message, String outboxMessageId, String traceparent, int attempt) {

  /**
   * The id consumers dedupe on: the envelope's {@code eventId}, or the {@code outbox-message-id}
   * header for envelopes written before 9.3.
   *
   * @return the event id, or null when neither is present
   */
  public String eventId() {
    String eventId = message.header().eventId();
    return eventId != null ? eventId : outboxMessageId;
  }

  /**
   * The producer's business deduplication key.
   *
   * @return the envelope's {@code idempotencyKey}, or null
   */
  public String idempotencyKey() {
    return message.header().idempotencyKey();
  }

  /**
   * The business payload.
   *
   * @return the body's payload
   */
  public T payload() {
    return message.body().payload();
  }

  /**
   * The payload schema version.
   *
   * @return the envelope's {@code schemaVersion}
   */
  public String schemaVersion() {
    return message.header().schemaVersion();
  }

  /**
   * Prints the event id and attempt only, because the payload and keys may carry references to
   * personal data.
   *
   * @return a log-safe description
   */
  @Override
  public String toString() {
    return "ReceivedEnvelope[eventId=" + eventId() + ", attempt=" + attempt + "]";
  }
}
