package io.codehunters.messaging;

import java.util.Objects;

/**
 * What an application asks the outbox to publish: an immutable command built fluently, so the
 * destination, the key, the event name and the payload are named rather than passed as positional
 * strings that are easy to transpose.
 *
 * <pre>{@code
 * OutboxEvent<OrderPlaced> event =
 *     OutboxEvent.to("orders").key(order.id()).type("OrderPlaced").payload(placed);
 * OutboxEvent<OrderPlaced> routed = event.routingKey("orders.placed");
 * }</pre>
 *
 * <p>The staged builder ({@link #to}, {@link Destination#key}, {@link Keyed#type}, {@link
 * Typed#payload}) makes topic, key, event name and payload mandatory; the remaining components have
 * defaults and are changed with the copy methods ({@link #routingKey(String)}, {@link
 * #eventType(String)}, {@link #locale(String)}, {@link #traceId(String)}, {@link
 * #schemaVersion(String)}, {@link #idempotencyKey(String)}), each returning a new event.
 *
 * @param topic the destination topic or exchange; never blank
 * @param key the aggregate identifier: the envelope's {@code aggregate} and the per-key ordering
 *     key ({@code message_key}); never blank
 * @param type the event name stamped on the envelope body, for example {@code OrderPlaced}; never
 *     blank
 * @param payload the business payload; never null
 * @param routingKey the AMQP routing key, or null for none (ignored by Kafka)
 * @param eventType {@link EventTypes#EVENT} (the default) or {@link EventTypes#COMMAND}
 * @param locale the locale stamped on the envelope header, or null (the default)
 * @param traceId the trace id stamped on the envelope header; null (the default) uses the current
 *     trace
 * @param schemaVersion the payload schema version stamped on the envelope header; {@value
 *     EventMessage.Header#DEFAULT_VERSION} by default
 * @param idempotencyKey the business deduplication key stamped on the envelope header, or null (the
 *     default); never blank when set
 * @param <T> the payload type
 */
public record OutboxEvent<T>(
    String topic,
    String key,
    String type,
    T payload,
    String routingKey,
    String eventType,
    String locale,
    String traceId,
    String schemaVersion,
    String idempotencyKey) {

  /**
   * Validates the mandatory components and applies the defaults of {@code eventType} and {@code
   * schemaVersion}.
   *
   * @throws IllegalArgumentException when topic, key or type is blank, or idempotencyKey is set but
   *     blank
   * @throws NullPointerException when a mandatory component is null
   */
  public OutboxEvent {
    requireText(topic, "topic");
    requireText(key, "key");
    requireText(type, "type");
    Objects.requireNonNull(payload, "payload");
    eventType = eventType == null ? EventTypes.EVENT : eventType;
    schemaVersion = schemaVersion == null ? EventMessage.Header.DEFAULT_VERSION : schemaVersion;
    if (idempotencyKey != null && idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey must not be blank");
    }
  }

  /**
   * The 9.2 signature: no idempotency key. Kept for source and binary compatibility.
   *
   * @param topic the destination topic or exchange
   * @param key the aggregate identifier
   * @param type the event name
   * @param payload the business payload
   * @param routingKey the AMQP routing key, or null
   * @param eventType the event type, or null for {@link EventTypes#EVENT}
   * @param locale the locale, or null
   * @param traceId the trace id, or null for the current trace
   * @param schemaVersion the payload schema version, or null for the default
   */
  public OutboxEvent(
      String topic,
      String key,
      String type,
      T payload,
      String routingKey,
      String eventType,
      String locale,
      String traceId,
      String schemaVersion) {
    this(topic, key, type, payload, routingKey, eventType, locale, traceId, schemaVersion, null);
  }

  /**
   * Starts an event for a destination.
   *
   * @param topic the destination topic or exchange
   * @return the next builder stage
   */
  public static Destination to(String topic) {
    return new Destination(requireText(topic, "topic"));
  }

  /**
   * A copy with an AMQP routing key.
   *
   * @param routingKey the routing key, or null for none
   * @return the new event
   */
  public OutboxEvent<T> routingKey(String routingKey) {
    return new OutboxEvent<>(
        topic,
        key,
        type,
        payload,
        routingKey,
        eventType,
        locale,
        traceId,
        schemaVersion,
        idempotencyKey);
  }

  /**
   * A copy with another event type.
   *
   * @param eventType {@link EventTypes#EVENT} or {@link EventTypes#COMMAND}
   * @return the new event
   */
  public OutboxEvent<T> eventType(String eventType) {
    return new OutboxEvent<>(
        topic,
        key,
        type,
        payload,
        routingKey,
        eventType,
        locale,
        traceId,
        schemaVersion,
        idempotencyKey);
  }

  /**
   * A copy with a locale.
   *
   * @param locale the locale stamped on the envelope header
   * @return the new event
   */
  public OutboxEvent<T> locale(String locale) {
    return new OutboxEvent<>(
        topic,
        key,
        type,
        payload,
        routingKey,
        eventType,
        locale,
        traceId,
        schemaVersion,
        idempotencyKey);
  }

  /**
   * A copy with an explicit trace id.
   *
   * @param traceId the trace id; null or blank uses the current trace
   * @return the new event
   */
  public OutboxEvent<T> traceId(String traceId) {
    return new OutboxEvent<>(
        topic,
        key,
        type,
        payload,
        routingKey,
        eventType,
        locale,
        traceId,
        schemaVersion,
        idempotencyKey);
  }

  /**
   * A copy with another payload schema version; bump it on a breaking payload change.
   *
   * @param schemaVersion the schema version
   * @return the new event
   */
  public OutboxEvent<T> schemaVersion(String schemaVersion) {
    return new OutboxEvent<>(
        topic,
        key,
        type,
        payload,
        routingKey,
        eventType,
        locale,
        traceId,
        schemaVersion,
        idempotencyKey);
  }

  /**
   * A copy with a business deduplication key, stamped on the envelope header for consumers.
   *
   * @param idempotencyKey the key, or null for none; never blank
   * @return the new event
   */
  public OutboxEvent<T> idempotencyKey(String idempotencyKey) {
    return new OutboxEvent<>(
        topic,
        key,
        type,
        payload,
        routingKey,
        eventType,
        locale,
        traceId,
        schemaVersion,
        idempotencyKey);
  }

  private static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }

  /**
   * Builder stage holding the destination.
   *
   * @param topic the destination topic or exchange
   */
  public record Destination(String topic) {

    /**
     * Sets the aggregate key.
     *
     * @param key the aggregate identifier and per-key ordering key
     * @return the next builder stage
     */
    public Keyed key(String key) {
      return new Keyed(topic, requireText(key, "key"));
    }
  }

  /**
   * Builder stage holding the destination and the key.
   *
   * @param topic the destination topic or exchange
   * @param key the aggregate identifier
   */
  public record Keyed(String topic, String key) {

    /**
     * Sets the event name.
     *
     * @param type the event name, for example {@code OrderPlaced}
     * @return the next builder stage
     */
    public Typed type(String type) {
      return new Typed(topic, key, requireText(type, "type"));
    }
  }

  /**
   * Builder stage holding the destination, the key and the event name.
   *
   * @param topic the destination topic or exchange
   * @param key the aggregate identifier
   * @param type the event name
   */
  public record Typed(String topic, String key, String type) {

    /**
     * Sets the payload and builds the event.
     *
     * @param payload the business payload
     * @param <T> the payload type
     * @return the event, with default routing key, event type, locale, trace id, schema version and
     *     idempotency key
     */
    public <T> OutboxEvent<T> payload(T payload) {
      return new OutboxEvent<>(topic, key, type, payload, null, null, null, null, null, null);
    }
  }
}
