package io.codehunters.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessagePublisherPortTest {

  private static final String TOPIC = "events.test";
  private static final String PAYLOAD = "{\"a\":1}";

  private RecordingPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new RecordingPublisher();
  }

  @Test
  void should_expose_outbox_id_header_name_when_adapters_tag_messages() {
    assertThat(MessagePublisherPort.OUTBOX_ID_HEADER).isEqualTo("outbox-message-id");
  }

  @Test
  void should_delegate_to_routing_key_overload_when_routing_key_present() {
    publisher.publish(message("user.created"));

    assertThat(publisher.calls).containsExactly(List.of(TOPIC, "user.created", PAYLOAD));
  }

  @Test
  void should_delegate_to_topic_overload_when_routing_key_null() {
    publisher.publish(message(null));

    assertThat(publisher.calls).containsExactly(List.of(TOPIC, PAYLOAD));
  }

  @Test
  void should_delegate_to_topic_overload_when_routing_key_blank() {
    publisher.publish(message("   "));

    assertThat(publisher.calls).containsExactly(List.of(TOPIC, PAYLOAD));
  }

  private static OutboxMessage<String> message(String routingKey) {
    return OutboxMessage.<String>builder()
        .id(UUID.randomUUID())
        .topic(TOPIC)
        .routingKey(routingKey)
        .key("k-1")
        .message(PAYLOAD)
        .build();
  }

  private static final class RecordingPublisher implements MessagePublisherPort {

    private final List<List<String>> calls = new ArrayList<>();

    @Override
    public void publish(String topic, String routingKey, String payload) {
      calls.add(List.of(topic, routingKey, payload));
    }

    @Override
    public void publish(String topic, String payload) {
      calls.add(List.of(topic, payload));
    }
  }
}
