package io.codehunters.messaging.factory;

import io.codehunters.messaging.EventMessage;
import io.codehunters.messaging.OutboxEvent;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.W3cTraceParent;
import java.util.UUID;

/**
 * Turns an {@link OutboxEvent} into an {@link OutboxMessage} ready to save: a new id (also the
 * envelope's {@code eventId}), the configured origin, the event's topic, routing key and key, the
 * {@link EventMessage} envelope, the clock's time as {@code occurredAt}, and the current W3C {@code
 * traceparent} from the event-message factory's {@link TraceIdSource} when it is a valid {@link
 * W3cTraceParent} and its trace id is the one the envelope names. Build messages on the thread that
 * does the work, so the stored context is that work's.
 */
public class OutboxEventFactory {

  private final EventMessageFactory eventMessageFactory;

  /**
   * Creates the factory.
   *
   * @param eventMessageFactory the factory of the envelope, whose origin, clock and trace source
   *     the outbox message shares
   */
  public OutboxEventFactory(EventMessageFactory eventMessageFactory) {
    this.eventMessageFactory = eventMessageFactory;
  }

  /**
   * Builds the outbox message of an event.
   *
   * <pre>{@code
   * outboxWriter.save(
   *     factory.create(OutboxEvent.to("orders").key(id).type("OrderPlaced").payload(placed)));
   * }</pre>
   *
   * @param event the event to publish
   * @param <T> the payload type
   * @return the outbox message wrapping the event's envelope
   */
  public <T> OutboxMessage<EventMessage<T>> create(OutboxEvent<T> event) {
    UUID id = UUID.randomUUID();
    EventMessage<T> envelope = eventMessageFactory.create(event, id.toString());
    return OutboxMessage.<EventMessage<T>>builder()
        .id(id)
        .origin(eventMessageFactory.origin())
        .topic(event.topic())
        .routingKey(event.routingKey())
        .key(event.key())
        .message(envelope)
        .occurredAt(envelope.header().timestamp())
        .traceparent(traceParentOf(envelope.header().traceId()))
        .build();
  }

  // An explicit trace id that is not the current trace gets no context: the broker header must
  // name the same trace as the envelope. An invalid current context is not stored either.
  private String traceParentOf(String traceId) {
    if (traceId == null) {
      return null;
    }
    return eventMessageFactory
        .traceIdSource()
        .currentTraceParent()
        .flatMap(W3cTraceParent::parse)
        .filter(traceparent -> traceparent.traceId().equals(traceId))
        .map(W3cTraceParent::value)
        .orElse(null);
  }
}
