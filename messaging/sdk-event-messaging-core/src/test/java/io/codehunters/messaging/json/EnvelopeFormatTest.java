package io.codehunters.messaging.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.codehunters.messaging.EventMessage;
import io.codehunters.messaging.EventTypes;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class EnvelopeFormatTest {

  private static final Instant AT = Instant.parse("2026-10-27T10:00:00Z");
  private static final EventMessage.Body<String> BODY =
      new EventMessage.Body<>("order-1", "OrderPlaced", EventTypes.EVENT, "p");
  private static final EventMessage<String> LEGACY =
      new EventMessage<>(new EventMessage.Header("t", "1.0.0", "1.0.0", "orders", AT, null), BODY);
  private static final EventMessage<String> IDENTIFIED =
      new EventMessage<>(
          new EventMessage.Header("t", "1.0.0", "1.0.0", "orders", AT, null, "evt-1", "k-1"), BODY);

  private final JsonMapper pinned = EnvelopeFormat.pin(JsonMapper.builder()).build();

  private final JsonMapper snakeCaseNonNull =
      EnvelopeFormat.pin(
              JsonMapper.builder()
                  .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                  .changeDefaultPropertyInclusion(
                      inclusion -> JsonInclude.Value.construct(Include.NON_NULL, Include.NON_NULL)))
          .build();

  @Test
  void should_write_null_event_id_and_idempotency_key_when_header_has_the_9_2_shape() {
    assertThat(pinned.writeValueAsString(LEGACY))
        .contains("\"eventId\":null")
        .contains("\"idempotencyKey\":null");
  }

  @Test
  void should_write_event_id_and_idempotency_key_when_header_carries_them() {
    assertThat(pinned.writeValueAsString(IDENTIFIED))
        .contains("\"eventId\":\"evt-1\"")
        .contains("\"idempotencyKey\":\"k-1\"");
  }

  @Test
  void should_keep_lower_camel_names_and_nulls_when_base_builder_uses_snake_case_non_null() {
    assertThat(snakeCaseNonNull.writeValueAsString(LEGACY))
        .contains("\"traceId\":\"t\"")
        .contains("\"schemaVersion\":\"1.0.0\"")
        .contains("\"eventName\":\"OrderPlaced\"")
        .contains("\"eventId\":null")
        .contains("\"locale\":null");
  }

  @Test
  void should_read_back_the_envelope_when_written_by_the_pinned_mapper() {
    String json = pinned.writeValueAsString(IDENTIFIED);

    EventMessage<?> read = pinned.readValue(json, EventMessage.class);

    assertThat(read.header()).isEqualTo(IDENTIFIED.header());
  }
}
