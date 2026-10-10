package io.codehunters.messaging.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.ClaimRequest;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.OutboxStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A publish interrupted by shutdown releases the batch instead of charging an attempt. */
class SendOutboxMessagesUseCaseInterruptTest {

  private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
  private static final String CLAIM_MARKER = "instance-1:marker";
  private static final int PREVIOUS_ATTEMPTS = 1;

  private FakeDispatchStore store;
  private RecordingPublisher publisher;
  private SendOutboxMessagesUseCase useCase;

  @BeforeEach
  void setUp() {
    MutableClock clock = new MutableClock(NOW);
    List<String> calls = new ArrayList<>();
    store = new FakeDispatchStore(clock, calls);
    publisher = new RecordingPublisher(clock, calls);
    DispatchSettings settings =
        new DispatchSettings(
            new ClaimRequest("origin", "instance-1", 10, Duration.ofMinutes(5), 10),
            new RetryPolicy(3, Duration.ofSeconds(30), Duration.ofHours(1)),
            clock);
    useCase = new SendOutboxMessagesUseCase(store, publisher, settings);
  }

  @AfterEach
  void clearInterrupt() {
    Thread.interrupted();
  }

  @Test
  void should_release_row_without_charging_attempt_when_publish_is_interrupted() {
    OutboxMessage<String> a1 = claimed("A");
    store.willClaim(List.of(a1));
    publisher.interruptOn(a1, true);

    useCase.execute();

    assertReleased(store.updated().getFirst());
  }

  @Test
  void should_release_row_when_publish_fails_with_interrupted_cause_and_cleared_flag() {
    OutboxMessage<String> a1 = claimed("A");
    store.willClaim(List.of(a1));
    publisher.interruptOn(a1, false);

    useCase.execute();

    assertReleased(store.updated().getFirst());
  }

  @Test
  void should_release_remaining_rows_unpublished_when_publish_is_interrupted() {
    OutboxMessage<String> a1 = claimed("A");
    OutboxMessage<String> b1 = claimed("B");
    store.willClaim(List.of(a1, b1));
    publisher.interruptOn(a1, true);

    useCase.execute();

    assertThat(publisher.published()).isEmpty();
    assertThat(store.updatedIds()).containsExactly(a1.id(), b1.id());
    assertReleased(store.updated().get(1));
  }

  @Test
  void should_keep_thread_interrupted_when_publish_is_interrupted() {
    OutboxMessage<String> a1 = claimed("A");
    store.willClaim(List.of(a1));
    publisher.interruptOn(a1, false);

    useCase.execute();

    assertThat(Thread.currentThread().isInterrupted()).isTrue();
  }

  private static void assertReleased(OutboxMessage<String> message) {
    assertThat(message.status()).isEqualTo(OutboxStatus.PENDING);
    assertThat(message.attempts()).isEqualTo(PREVIOUS_ATTEMPTS);
    assertThat(message.error()).isNull();
    assertThat(message.nextAttemptAt()).isEqualTo(NOW);
  }

  private static OutboxMessage<String> claimed(String key) {
    return OutboxMessage.<String>builder()
        .id(UUID.randomUUID())
        .origin("origin")
        .topic("orders")
        .key(key)
        .message("payload")
        .status(OutboxStatus.IN_FLIGHT)
        .attempts(PREVIOUS_ATTEMPTS)
        .claimedAt(NOW)
        .claimedBy(CLAIM_MARKER)
        .build();
  }
}
