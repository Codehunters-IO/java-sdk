package io.codehunters.messaging.domain.factory;

import io.codehunters.messaging.domain.model.EventMessage;

/** Builds {@link EventMessage} instances, stamping the configured origin on the header. */
public class EventMessageFactory {

  private final String origin;

  /**
   * Creates the factory.
   *
   * @param origin the origin service identifier stamped on every event header
   */
  public EventMessageFactory(String origin) {
    this.origin = origin;
  }

  /**
   * Builds an event message with no locale.
   *
   * @param traceId the trace identifier
   * @param aggregate the aggregate identifier
   * @param eventName the event name
   * @param eventType the event type
   * @param payload the event body
   * @param <T> the payload type
   * @return the built {@link EventMessage}
   */
  public <T> EventMessage<T> create(
      String traceId, String aggregate, String eventName, String eventType, T payload) {
    return create(traceId, null, aggregate, eventName, eventType, payload);
  }

  /**
   * Builds an event message with an explicit locale.
   *
   * @param traceId the trace identifier
   * @param locale the locale
   * @param aggregate the aggregate identifier
   * @param eventName the event name
   * @param eventType the event type
   * @param payload the event body
   * @param <T> the payload type
   * @return the built {@link EventMessage}
   */
  public <T> EventMessage<T> create(
      String traceId,
      String locale,
      String aggregate,
      String eventName,
      String eventType,
      T payload) {
    return EventMessage.<T>builder()
        .header(
            EventMessage.Header.builder().traceId(traceId).origin(origin).locale(locale).build())
        .body(
            EventMessage.Body.<T>builder()
                .aggregate(aggregate)
                .eventName(eventName)
                .eventType(eventType)
                .payload(payload)
                .build())
        .build();
  }
}
