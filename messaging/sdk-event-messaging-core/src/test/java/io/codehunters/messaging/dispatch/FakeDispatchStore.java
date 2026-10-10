package io.codehunters.messaging.dispatch;

import io.codehunters.messaging.ClaimRequest;
import io.codehunters.messaging.OutboxDispatchStore;
import io.codehunters.messaging.OutboxMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * In-memory {@link OutboxDispatchStore} that hands out a prepared batch and records the claim
 * requests, the send marks and the updates in order. Marks of chosen rows can be refused (claim
 * taken over) or fail.
 */
final class FakeDispatchStore implements OutboxDispatchStore {

  private final MutableClock clock;
  private final List<String> calls;
  private final List<ClaimRequest> claimRequests = new ArrayList<>();
  private final List<OutboxMessage<String>> updated = new ArrayList<>();
  private final List<OutboxMessage<String>> started = new ArrayList<>();
  private final Map<UUID, Function<OutboxMessage<String>, Optional<OutboxMessage<String>>>>
      markOutcomes = new HashMap<>();
  private final Map<UUID, RuntimeException> updateFailures = new HashMap<>();
  private List<OutboxMessage<String>> toClaim = List.of();
  private Duration claimDuration = Duration.ZERO;

  FakeDispatchStore(MutableClock clock, List<String> calls) {
    this.clock = clock;
    this.calls = calls;
  }

  void willClaim(List<OutboxMessage<String>> messages) {
    toClaim = messages;
  }

  void claimTakes(Duration duration) {
    claimDuration = duration;
  }

  void refuseMark(OutboxMessage<String> message) {
    markOutcomes.put(message.id(), ignored -> Optional.empty());
  }

  void failMark(OutboxMessage<String> message, RuntimeException failure) {
    markOutcomes.put(
        message.id(),
        ignored -> {
          throw failure;
        });
  }

  void failUpdate(OutboxMessage<String> message, RuntimeException failure) {
    updateFailures.put(message.id(), failure);
  }

  List<ClaimRequest> claimRequests() {
    return claimRequests;
  }

  List<OutboxMessage<String>> updated() {
    return updated;
  }

  List<UUID> updatedIds() {
    return updated.stream().map(OutboxMessage::id).toList();
  }

  List<UUID> startedIds() {
    return started.stream().map(OutboxMessage::id).toList();
  }

  @Override
  public List<OutboxMessage<String>> claim(ClaimRequest request) {
    claimRequests.add(request);
    clock.advance(claimDuration);
    return toClaim;
  }

  @Override
  public Optional<OutboxMessage<String>> markSendStarted(OutboxMessage<String> outboxMessage) {
    calls.add("mark " + outboxMessage.id());
    started.add(outboxMessage);
    return markOutcomes.getOrDefault(outboxMessage.id(), this::marked).apply(outboxMessage);
  }

  @Override
  public void update(OutboxMessage<String> outboxMessage) {
    calls.add("update " + outboxMessage.id());
    RuntimeException failure = updateFailures.get(outboxMessage.id());
    if (failure != null) {
      throw failure;
    }
    updated.add(outboxMessage);
  }

  @Override
  public int purgePublished(Instant cutoff, int batchSize) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long countFailed() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int requeue(Collection<UUID> ids) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int requeueFailed(String topic, String key) {
    throw new UnsupportedOperationException();
  }

  private Optional<OutboxMessage<String>> marked(OutboxMessage<String> message) {
    return Optional.of(message.sendStarted(clock.instant()));
  }
}
