package io.codehunters.messaging.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.domain.model.OutboxMessage;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MeteredMessagePublisherTest {

  private static final OutboxMessage<String> MESSAGE =
      OutboxMessage.<String>builder().topic("events").key("k").message("{}").build();

  private SimpleMeterRegistry registry;
  private RecordingPublisher delegate;
  private MeteredMessagePublisher publisher;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    delegate = new RecordingPublisher();
    publisher = new MeteredMessagePublisher(delegate, registry);
  }

  @Test
  void should_delegate_whole_message_and_time_success_when_publish_succeeds() {
    publisher.publish(MESSAGE);

    assertThat(delegate.calls).containsExactly("message:events");
    assertThat(timer("success", "none").count()).isEqualTo(1);
  }

  @Test
  void should_time_success_when_publishing_with_routing_key() {
    publisher.publish("events", "rk", "{}");

    assertThat(delegate.calls).containsExactly("routed:events:rk");
    assertThat(timer("success", "none").count()).isEqualTo(1);
  }

  @Test
  void should_time_success_when_publishing_without_routing_key() {
    publisher.publish("events", "{}");

    assertThat(delegate.calls).containsExactly("plain:events");
    assertThat(timer("success", "none").count()).isEqualTo(1);
  }

  @Test
  void should_rethrow_and_time_failure_with_exception_name_when_publish_fails() {
    delegate.failWith(new IllegalStateException("broker down"));

    assertThatThrownBy(() -> publisher.publish(MESSAGE)).isInstanceOf(IllegalStateException.class);
    assertThat(timer("failure", "IllegalStateException").count()).isEqualTo(1);
    assertThat(registry.find(MeteredMessagePublisher.TIMER).tag("outcome", "success").timer())
        .isNull();
  }

  private Timer timer(String outcome, String exception) {
    return registry
        .get(MeteredMessagePublisher.TIMER)
        .tag("outcome", outcome)
        .tag("exception", exception)
        .timer();
  }

  /** Publisher that records which overload was called and can be told to fail. */
  private static final class RecordingPublisher implements MessagePublisherPort {
    private final List<String> calls = new ArrayList<>();
    private RuntimeException failure;

    void failWith(RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public void publish(String topic, String routingKey, String payload) {
      record("routed:" + topic + ":" + routingKey);
    }

    @Override
    public void publish(String topic, String payload) {
      record("plain:" + topic);
    }

    @Override
    public void publish(OutboxMessage<String> message) {
      record("message:" + message.getTopic());
    }

    private void record(String call) {
      if (failure != null) {
        throw failure;
      }
      calls.add(call);
    }
  }
}
