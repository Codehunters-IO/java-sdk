package io.codehunters.messaging.dispatch;

import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Publisher that records whole-message publishes, takes a fixed time on the shared clock and can be
 * told to fail. The string overloads must never be used by the dispatcher.
 */
final class RecordingPublisher implements MessagePublisherPort {

  private final MutableClock clock;
  private final List<String> calls;
  private final List<OutboxMessage<String>> published = new ArrayList<>();
  private Duration publishTime = Duration.ZERO;
  private RuntimeException failure;

  RecordingPublisher(MutableClock clock, List<String> calls) {
    this.clock = clock;
    this.calls = calls;
  }

  void takes(Duration duration) {
    publishTime = duration;
  }

  void failWith(RuntimeException failure) {
    this.failure = failure;
  }

  List<OutboxMessage<String>> published() {
    return published;
  }

  List<UUID> publishedIds() {
    return published.stream().map(OutboxMessage::id).toList();
  }

  @Override
  public void publish(String topic, String routingKey, String payload) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void publish(String topic, String payload) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void publish(OutboxMessage<String> message) {
    calls.add("publish " + message.id());
    clock.advance(publishTime);
    if (failure != null) {
      throw failure;
    }
    published.add(message);
  }
}
