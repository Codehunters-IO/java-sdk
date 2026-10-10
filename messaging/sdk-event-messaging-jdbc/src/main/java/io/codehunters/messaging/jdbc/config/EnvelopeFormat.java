package io.codehunters.messaging.jdbc.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.codehunters.messaging.EventMessage;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pins the {@link EventMessage} envelope ({@code header}, {@code body} and their fields) to
 * lower-camel-case names with every field written, nulls included, whatever naming strategy or
 * inclusion the application's Jackson customizers set. The payload keeps following the
 * application's mapper. Registered only on the {@code outboxJsonMapper}.
 */
final class EnvelopeFormat {

  private EnvelopeFormat() {}

  /**
   * Adds the envelope mixins to the builder.
   *
   * @param builder the outbox mapper's builder
   * @return the same builder
   */
  static JsonMapper.Builder pin(JsonMapper.Builder builder) {
    return builder
        .addMixIn(EventMessage.class, Pinned.class)
        .addMixIn(EventMessage.Header.class, Pinned.class)
        .addMixIn(EventMessage.Body.class, Pinned.class);
  }

  @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
  @JsonInclude(JsonInclude.Include.ALWAYS)
  private abstract static class Pinned {}
}
