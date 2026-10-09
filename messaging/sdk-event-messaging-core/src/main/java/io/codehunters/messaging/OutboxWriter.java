package io.codehunters.messaging;

/**
 * Application-facing outbound port that stores outbox messages. Call it inside the transaction of
 * the business write, so the message is committed, or rolled back, with it.
 *
 * <pre>{@code
 * outboxWriter.save(
 *     outboxEventFactory.create(
 *         OutboxEvent.to("orders").key(order.id()).type("OrderPlaced").payload(placed)));
 * }</pre>
 */
@FunctionalInterface
public interface OutboxWriter {

  /**
   * Persists a new outbox message as {@link OutboxStatus#PENDING}. A non-string body is serialized
   * as JSON.
   *
   * @param outboxMessage the message to persist
   * @param <T> the message body type
   */
  <T> void save(OutboxMessage<T> outboxMessage);
}
