package io.codehunters.messaging.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.codehunters.messaging.EventMessage;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins the {@link EventMessage} envelope ({@code header}, {@code body} and their fields) to
 * lower-camel-case names with every field written, nulls included, whatever naming strategy or
 * inclusion the application's Jackson customizers set. The payload keeps following the
 * application's mapper.
 *
 * <p>This is the single wire definition of the envelope: writers ({@code outboxJsonMapper} in
 * {@code sdk-event-messaging-jdbc}) and readers (the consumer in {@code sdk-event-messaging-kafka})
 * pin their mappers with it, so both sides agree on the field names, {@code eventId} and {@code
 * idempotencyKey} included, which are written as {@code null} when absent.
 */
public final class EnvelopeFormat {

  private EnvelopeFormat() {}

  /**
   * Adds the envelope mixins to the builder.
   *
   * @param builder the mapper's builder
   * @return the same builder
   */
  public static JsonMapper.Builder pin(JsonMapper.Builder builder) {
    return builder
        .addMixIn(EventMessage.class, Pinned.class)
        .addMixIn(EventMessage.Header.class, Pinned.class)
        .addMixIn(EventMessage.Body.class, Pinned.class);
  }

  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  @JsonInclude(JsonInclude.Include.ALWAYS)
  private abstract static class Pinned {}
}
