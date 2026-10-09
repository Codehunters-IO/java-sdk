package io.codehunters.messaging.factory;

import io.codehunters.messaging.EventMessage;
import io.codehunters.messaging.OutboxMessage;

/**
 * Wraps an {@link EventMessage} into an {@link OutboxMessage} ready to persist, stamping the
 * configured origin, topic and key, and the current W3C {@code traceparent} from the event-message
 * factory's {@link TraceIdSource} when it belongs to the trace the event names. Build messages on
 * the thread that does the work, so the stored context is that work's.
 */
public class OutboxEventFactory {

  private final String origin;
  private final EventMessageFactory eventMessageFactory;

  /**
   * Creates the factory.
   *
   * @param origin the origin service identifier stamped on every outbox message
   * @param eventMessageFactory the factory used to build the inner event payload
   */
  public OutboxEventFactory(String origin, EventMessageFactory eventMessageFactory) {
    this.origin = origin;
    this.eventMessageFactory = eventMessageFactory;
  }

  /**
   * Creates the factory with its own event-message factory, which uses the current trace id when
   * the caller passes none.
   *
   * @param origin the origin service identifier stamped on every outbox message and event header
   * @param traceIdSource supplies the trace id when the caller passes none
   */
  public OutboxEventFactory(String origin, TraceIdSource traceIdSource) {
    this(origin, new EventMessageFactory(origin, traceIdSource));
  }

  /**
   * Builds an outbox message with a routing key, defaulting the locale to {@code "es"}.
   *
   * @param topic the destination topic or exchange
   * @param routingKey the routing key
   * @param traceId the trace identifier propagated in the event header; null or blank uses the
   *     current trace when the event-message factory has a {@link TraceIdSource}
   * @param aggregate the aggregate identifier (also used as message key)
   * @param eventName the event name
   * @param eventType the event type
   * @param payload the event body
   * @param <T> the payload type
   * @return the outbox message wrapping the built event
   */
  public <T> OutboxMessage<EventMessage<T>> createRoutingKey(
      String topic,
      String routingKey,
      String traceId,
      String aggregate,
      String eventName,
      String eventType,
      T payload) {
    return createRoutingKey(
        topic, routingKey, "es", traceId, aggregate, eventName, eventType, payload);
  }

  /**
   * Builds an outbox message with a routing key and an explicit locale.
   *
   * @param topic the destination topic or exchange
   * @param routingKey the routing key
   * @param locale the locale propagated in the event header
   * @param traceId the trace identifier propagated in the event header; null or blank uses the
   *     current trace when the event-message factory has a {@link TraceIdSource}
   * @param aggregate the aggregate identifier (also used as message key)
   * @param eventName the event name
   * @param eventType the event type
   * @param payload the event body
   * @param <T> the payload type
   * @return the outbox message wrapping the built event
   */
  public <T> OutboxMessage<EventMessage<T>> createRoutingKey(
      String topic,
      String routingKey,
      String locale,
      String traceId,
      String aggregate,
      String eventName,
      String eventType,
      T payload) {
    EventMessage<T> event =
        eventMessageFactory.create(traceId, locale, aggregate, eventName, eventType, payload);
    return outboxMessage(topic, routingKey, aggregate, event);
  }

  /**
   * Builds an outbox message without a routing key.
   *
   * @param topic the destination topic or exchange
   * @param traceId the trace identifier propagated in the event header; null or blank uses the
   *     current trace when the event-message factory has a {@link TraceIdSource}
   * @param aggregate the aggregate identifier (also used as message key)
   * @param eventName the event name
   * @param eventType the event type
   * @param payload the event body
   * @param <T> the payload type
   * @return the outbox message wrapping the built event
   */
  public <T> OutboxMessage<EventMessage<T>> create(
      String topic,
      String traceId,
      String aggregate,
      String eventName,
      String eventType,
      T payload) {
    EventMessage<T> event =
        eventMessageFactory.create(traceId, "es", aggregate, eventName, eventType, payload);
    return outboxMessage(topic, null, aggregate, event);
  }

  // Returns the built message, not Lombok's builder: javadoc runs on the undelomboked sources.
  private <T> OutboxMessage<EventMessage<T>> outboxMessage(
      String topic, String routingKey, String aggregate, EventMessage<T> event) {
    return OutboxMessage.<EventMessage<T>>builder()
        .origin(origin)
        .topic(topic)
        .routingKey(routingKey)
        .key(aggregate)
        .message(event)
        .traceparent(traceParentOf(event.getHeader().getTraceId()))
        .build();
  }

  // An explicit trace id that is not the current trace gets no context: the broker header must
  // name the same trace as the envelope.
  private String traceParentOf(String traceId) {
    if (traceId == null) {
      return null;
    }
    return eventMessageFactory
        .traceIdSource()
        .currentTraceParent()
        .filter(traceparent -> traceparent.contains("-" + traceId + "-"))
        .orElse(null);
  }
}
