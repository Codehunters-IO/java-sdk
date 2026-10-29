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
   * @param eventId unique id of this event; equals the outbox row id when built by {@code
   *     OutboxEventFactory}. Consumers dedupe on it and fall back to the {@code outbox-message-id}
   *     header for envelopes written before 9.3
   * @param idempotencyKey optional business deduplication key set by the producer, or null
   */
  public record Header(
      String traceId,
      String version,
      String schemaVersion,
      String origin,
      Instant timestamp,
      String locale,
      String eventId,
      String idempotencyKey) {

    /** Envelope and schema version used when none is given. */
    public static final String DEFAULT_VERSION = "1.0.0";

    /**
     * The 9.2 signature: no event id and no idempotency key. Kept for source and binary
     * compatibility.
     *
     * @param traceId the trace id of the work that created the event, or null
     * @param version the envelope version
     * @param schemaVersion the payload schema version
     * @param origin the application that created the event
     * @param timestamp when the event was created
     * @param locale the locale of the work that created the event, or null
     */
    public Header(
        String traceId,
        String version,
        String schemaVersion,
        String origin,
        Instant timestamp,
        String locale) {
      this(traceId, version, schemaVersion, origin, timestamp, locale, null, null);
    }
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
