package io.codehunters.messaging.factory;

import io.codehunters.messaging.EventMessage;
import io.codehunters.messaging.OutboxEvent;
import java.time.Clock;

/**
 * Builds {@link EventMessage} envelopes from an {@link OutboxEvent}, stamping the configured origin
 * and the clock's time on the header. A null or blank trace id is replaced by the current one from
 * the {@link TraceIdSource}, when there is one.
 */
public class EventMessageFactory {

  private final String origin;
  private final TraceIdSource traceIdSource;
  private final Clock clock;

  /**
   * Creates the factory.
   *
   * @param origin the origin service identifier stamped on every event header
   * @param traceIdSource supplies the trace id when the event carries none
   * @param clock the clock of the header timestamp
   */
  public EventMessageFactory(String origin, TraceIdSource traceIdSource, Clock clock) {
    this.origin = origin;
    this.traceIdSource = traceIdSource;
    this.clock = clock;
  }

  /**
   * Builds the envelope of an event: its key becomes the body's {@code aggregate} and its type the
   * body's {@code eventName}.
   *
   * @param event the event to wrap
   * @param <T> the payload type
   * @return the envelope
   */
  public <T> EventMessage<T> create(OutboxEvent<T> event) {
    EventMessage.Header header =
        new EventMessage.Header(
            resolveTraceId(event.traceId()),
            EventMessage.Header.DEFAULT_VERSION,
            event.schemaVersion(),
            origin,
            clock.instant(),
            event.locale());
    return new EventMessage<>(
        header,
        new EventMessage.Body<>(event.key(), event.type(), event.eventType(), event.payload()));
  }

  // The outbox factory stamps the same origin, time and trace context on its rows.
  String origin() {
    return origin;
  }

  TraceIdSource traceIdSource() {
    return traceIdSource;
  }

  Clock clock() {
    return clock;
  }

  private String resolveTraceId(String traceId) {
    if (traceId != null && !traceId.isBlank()) {
      return traceId;
    }
    return traceIdSource.currentTraceId().orElse(traceId);
  }
}
