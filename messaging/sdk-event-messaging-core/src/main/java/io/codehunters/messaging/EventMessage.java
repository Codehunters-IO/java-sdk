package io.codehunters.messaging;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Domain event published to the message broker. Composed of a {@link Header} carrying tracing and
 * provenance metadata and a typed {@link Body} carrying the business payload.
 *
 * @param <T> the payload type
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EventMessage<T> {

  private Header header;
  private Body<T> body;

  /** Event header carrying trace identifiers, schema version, origin, timestamp and locale. */
  @Getter
  @Setter
  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  @Builder
  public static class Header {
    private String traceId;
    @Builder.Default private String version = "1.0.0";
    @Builder.Default private String schemaVersion = "1.0.0";
    private String origin;
    @Builder.Default private String timestamp = Instant.now().toString();
    private String locale;
  }

  /**
   * Event body carrying the aggregate identifier, event name and type, and the typed payload.
   *
   * @param <T> the payload type
   */
  @Getter
  @Setter
  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  @AllArgsConstructor(access = AccessLevel.PRIVATE)
  @Builder
  public static class Body<T> {
    private String aggregate;
    private String eventName;
    private String eventType;
    private T payload;
  }
}
