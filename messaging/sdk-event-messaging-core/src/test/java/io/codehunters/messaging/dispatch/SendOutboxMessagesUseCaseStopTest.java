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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A stopped use case ends its batch after the current send and claims nothing until resumed. */
class SendOutboxMessagesUseCaseStopTest {

  private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");
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

  @Test
  void should_finish_current_send_and_release_rest_of_batch_when_stopped_mid_batch() {
    OutboxMessage<String> a1 = claimed("A");
    OutboxMessage<String> b1 = claimed("B");
    OutboxMessage<String> c1 = claimed("C");
    store.willClaim(List.of(a1, b1, c1));
    publisher.whilePublishing(a1, useCase::stop);

    useCase.execute();

    assertThat(publisher.publishedIds()).containsExactly(a1.id());
    assertThat(store.updated())
        .extracting(OutboxMessage::status)
        .containsExactly(OutboxStatus.PUBLISHED, OutboxStatus.PENDING, OutboxStatus.PENDING);
    assertThat(store.updated().get(1).attempts()).isEqualTo(PREVIOUS_ATTEMPTS);
  }

  @Test
  void should_claim_nothing_when_stopped_before_run() {
    store.willClaim(List.of(claimed("A")));
    useCase.stop();

    boolean more = useCase.execute();

    assertThat(more).isFalse();
    assertThat(store.claimRequests()).isEmpty();
    assertThat(publisher.published()).isEmpty();
  }

  @Test
  void should_claim_and_publish_again_when_resumed_after_stop() {
    OutboxMessage<String> a1 = claimed("A");
    store.willClaim(List.of(a1));
    useCase.stop();
    useCase.resume();

    useCase.execute();

    assertThat(publisher.publishedIds()).containsExactly(a1.id());
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
        .claimedBy("instance-1:marker")
        .build();
  }
}
