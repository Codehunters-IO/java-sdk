package io.codehunters.messaging.dispatch;

import io.codehunters.messaging.MessagePublishException;
import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Publisher that records whole-message publishes, takes a fixed time on the shared clock and can be
 * told to fail, for every message or for chosen ones. The string overloads must never be used by
 * the dispatcher.
 */
final class RecordingPublisher implements MessagePublisherPort {

  private final MutableClock clock;
  private final List<String> calls;
  private final List<OutboxMessage<String>> published = new ArrayList<>();
  private Duration publishTime = Duration.ZERO;
  private final Set<UUID> failing = new HashSet<>();
  private RuntimeException failure;
  private final Map<UUID, Boolean> interrupting = new HashMap<>();

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

  void interruptOn(OutboxMessage<String> message, boolean keepsFlag) {
    interrupting.put(message.id(), keepsFlag);
  }

  void failFor(OutboxMessage<String> message) {
    failing.add(message.id());
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
    if (interrupting.containsKey(message.id())) {
      interrupt(interrupting.get(message.id()));
    }
    if (failing.contains(message.id())) {
      throw new IllegalStateException("broker refused " + message.id());
    }
    published.add(message);
  }

  // Kafka's adapter restores the interrupt flag; a client that swallows it leaves only the cause.
  private static void interrupt(boolean keepsFlag) {
    if (keepsFlag) {
      Thread.currentThread().interrupt();
    }
    throw new MessagePublishException(new InterruptedException("shutting down"));
  }
}
