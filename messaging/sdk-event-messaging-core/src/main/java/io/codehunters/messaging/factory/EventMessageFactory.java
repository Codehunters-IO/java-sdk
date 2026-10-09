package io.codehunters.messaging.factory;

import io.codehunters.messaging.EventMessage;

/**
 * Builds {@link EventMessage} instances, stamping the configured origin on the header. A null or
 * blank trace id is replaced by the current one from the {@link TraceIdSource}, when there is one.
 */
public class EventMessageFactory {

  private final String origin;
  private final TraceIdSource traceIdSource;

  /**
   * Creates the factory without a current-trace fallback.
   *
   * @param origin the origin service identifier stamped on every event header
   */
  public EventMessageFactory(String origin) {
    this(origin, TraceIdSource.none());
  }

  /**
   * Creates the factory.
   *
   * @param origin the origin service identifier stamped on every event header
   * @param traceIdSource supplies the trace id when the caller passes none
   */
  public EventMessageFactory(String origin, TraceIdSource traceIdSource) {
    this.origin = origin;
    this.traceIdSource = traceIdSource;
  }

  /**
   * Builds an event message with no locale.
   *
   * @param traceId the trace identifier; null or blank uses the current trace
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
   * @param traceId the trace identifier; null or blank uses the current trace
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
            EventMessage.Header.builder()
                .traceId(resolveTraceId(traceId))
                .origin(origin)
                .locale(locale)
                .build())
        .body(
            EventMessage.Body.<T>builder()
                .aggregate(aggregate)
                .eventName(eventName)
                .eventType(eventType)
                .payload(payload)
                .build())
        .build();
  }

  // The outbox factory reads the same source to store the current trace context with its rows.
  TraceIdSource traceIdSource() {
    return traceIdSource;
  }

  private String resolveTraceId(String traceId) {
    if (traceId != null && !traceId.isBlank()) {
      return traceId;
    }
    return traceIdSource.currentTraceId().orElse(traceId);
  }
}
