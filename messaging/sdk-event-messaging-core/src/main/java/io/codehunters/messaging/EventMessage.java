package io.codehunters.messaging;

import java.time.Instant;

/**
 * Domain event published to the message broker. Composed of a {@link Header} carrying tracing and
 * provenance metadata and a typed {@link Body} carrying the business payload. Immutable; serialized
 * as JSON with the timestamp as an ISO-8601 string.
 *
 * @param header the provenance and tracing metadata
 * @param body the aggregate, event name and type, and payload
 * @param <T> the payload type
 */
public record EventMessage<T>(Header header, Body<T> body) {

  /**
   * Event header carrying trace identifiers, schema version, origin, timestamp and locale.
   *
   * @param traceId the trace id of the work that created the event, or null
   * @param version the envelope version
   * @param schemaVersion the payload schema version; bump it on a breaking payload change
   * @param origin the application that created the event
   * @param timestamp when the event was created, from the outbox clock
   * @param locale the locale of the work that created the event, or null
   */
  public record Header(
      String traceId,
      String version,
      String schemaVersion,
      String origin,
      Instant timestamp,
      String locale) {

    /** Envelope and schema version used when none is given. */
    public static final String DEFAULT_VERSION = "1.0.0";
  }

  /**
   * Event body carrying the aggregate identifier, event name and type, and the typed payload.
   *
   * @param aggregate the aggregate identifier
   * @param eventName the event name, for example {@code OrderPlaced}
   * @param eventType {@link EventTypes#EVENT} or {@link EventTypes#COMMAND}
   * @param payload the business payload
   * @param <T> the payload type
   */
  public record Body<T>(String aggregate, String eventName, String eventType, T payload) {}
}
