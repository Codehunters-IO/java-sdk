package io.codehunters.audit.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.audit.AuditEvent;
import io.codehunters.audit.AuditEventPublisherPort;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class JsonSnapshotAuditEventPublisherTest {

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private final List<AuditEvent> delivered = new ArrayList<>();
  private final List<AuditEvent> detached = new ArrayList<>();
  private final JsonSnapshotAuditEventPublisher publisher =
      new JsonSnapshotAuditEventPublisher(new Recording(), jsonMapper);

  @Test
  void should_hand_on_a_payload_that_ignores_later_changes_to_the_argument() {
    List<String> items = new ArrayList<>(List.of("first"));

    publisher.publish(event(Map.of("items", items)));
    items.add("added after the call");

    assertThat(jsonMapper.writeValueAsString(delivered.getFirst().payload()))
        .isEqualTo("{\"items\":[\"first\"]}");
  }

  @Test
  void should_hand_a_detached_event_on_detached_with_a_copied_payload() {
    List<String> items = new ArrayList<>(List.of("first"));

    publisher.publishDetached(event(Map.of("items", items)));
    items.add("added after the call");

    assertThat(delivered).isEmpty();
    assertThat(jsonMapper.writeValueAsString(detached.getFirst().payload()))
        .isEqualTo("{\"items\":[\"first\"]}");
  }

  @Test
  void should_drop_the_payload_when_it_cannot_be_serialized() {
    publisher.publish(event(Map.of("bad", new AuditEventJdbcAdapterTest.Unserializable())));

    assertThat(delivered.getFirst().payload()).isNull();
  }

  @Test
  void should_keep_the_rest_of_the_event() {
    AuditEvent event = event(Map.of("amount", 10));

    publisher.publish(event);

    assertThat(delivered.getFirst())
        .usingRecursiveComparison()
        .ignoringFields("payload")
        .isEqualTo(event);
  }

  @Test
  void should_pass_an_empty_payload_through() {
    publisher.publish(event(Map.of()));

    assertThat(delivered.getFirst().payload()).isEmpty();
  }

  private static AuditEvent event(Map<String, Object> payload) {
    return new AuditEvent(
        "ORDER",
        "ORD-1",
        "CREATE",
        "user-1",
        "USER",
        null,
        payload,
        "SUCCESS",
        null,
        "order-service",
        OffsetDateTime.parse("2026-10-06T12:00:00Z"));
  }

  private final class Recording implements AuditEventPublisherPort {

    @Override
    public void publish(AuditEvent event) {
      delivered.add(event);
    }

    @Override
    public void publishDetached(AuditEvent event) {
      detached.add(event);
    }
  }
}
