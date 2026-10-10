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

class SendOutboxMessagesUseCaseGuardsTest {

  private static final int MAX_ATTEMPTS = 3;
  private static final Duration CLAIM_TTL = Duration.ofSeconds(10);
  private static final Instant START = Instant.parse("2026-10-05T10:00:00Z");
  private static final Duration PUBLISH_TIME = Duration.ofSeconds(3);
  private static final Instant BUDGET_SPENT_AT = START.plus(PUBLISH_TIME.multipliedBy(2));
  private static final String CLAIM_MARKER = "instance-1:marker";

  private List<String> calls;
  private FakeDispatchStore store;
  private RecordingPublisher publisher;
  private SendOutboxMessagesUseCase useCase;

  @BeforeEach
  void setUp() {
    MutableClock clock = new MutableClock(START);
    calls = new ArrayList<>();
    store = new FakeDispatchStore(clock, calls);
    publisher = new RecordingPublisher(clock, calls);
    publisher.takes(PUBLISH_TIME);
    DispatchSettings settings =
        new DispatchSettings(
            new ClaimRequest("origin", "instance-1", 10, CLAIM_TTL, 1),
            new RetryPolicy(MAX_ATTEMPTS, Duration.ofSeconds(1), Duration.ofMinutes(1)),
            clock);
    useCase = new SendOutboxMessagesUseCase(store, publisher, settings);
  }

  @Test
  void should_mark_failed_without_publishing_when_reclaimed_message_reached_max_attempts() {
    OutboxMessage<String> exhausted = claimed(MAX_ATTEMPTS);
    store.willClaim(List.of(exhausted));

    useCase.execute();

    assertThat(publisher.published()).isEmpty();
    assertThat(store.updated())
        .singleElement()
        .satisfies(
            failed -> {
              assertThat(failed.id()).isEqualTo(exhausted.id());
              assertThat(failed.status()).isEqualTo(OutboxStatus.FAILED);
              assertThat(failed.error()).isEqualTo("max-attempts-exceeded");
              assertThat(failed.attempts()).isEqualTo(MAX_ATTEMPTS);
              assertThat(failed.nextAttemptAt()).isNull();
            });
  }

  @Test
  void should_mark_failed_without_publishing_when_reclaimed_message_exceeded_max_attempts() {
    store.willClaim(List.of(claimed(MAX_ATTEMPTS + 2)));

    useCase.execute();

    assertThat(publisher.published()).isEmpty();
    assertThat(store.updated().getFirst().status()).isEqualTo(OutboxStatus.FAILED);
  }

  @Test
  void should_publish_message_when_attempts_below_max() {
    OutboxMessage<String> retried = claimed(MAX_ATTEMPTS - 1);
    store.willClaim(List.of(retried));

    useCase.execute();

    assertThat(publisher.publishedIds()).containsExactly(retried.id());
    assertThat(store.updated().getFirst().status()).isEqualTo(OutboxStatus.PUBLISHED);
  }

  @Test
  void should_release_remaining_messages_when_half_claim_ttl_elapsed() {
    OutboxMessage<String> first = claimed(0);
    OutboxMessage<String> second = claimed(0);
    OutboxMessage<String> third = claimed(1);
    OutboxMessage<String> fourth = claimed(0);
    store.willClaim(List.of(first, second, third, fourth));

    useCase.execute();

    assertThat(publisher.publishedIds()).containsExactly(first.id(), second.id());
    assertThat(store.updatedIds())
        .containsExactly(first.id(), second.id(), third.id(), fourth.id());
    assertReleased(store.updated().get(2), 1, BUDGET_SPENT_AT);
    assertReleased(store.updated().get(3), 0, BUDGET_SPENT_AT);
  }

  @Test
  void should_count_claim_time_against_budget_when_claim_is_slow() {
    store.willClaim(List.of(claimed(0)));
    store.claimTakes(CLAIM_TTL.dividedBy(2));

    useCase.execute();

    assertThat(publisher.published()).isEmpty();
    assertReleased(store.updated().getFirst(), 0, START.plus(CLAIM_TTL.dividedBy(2)));
  }

  @Test
  void should_not_mark_send_started_when_reclaimed_message_reached_max_attempts() {
    store.willClaim(List.of(claimed(MAX_ATTEMPTS)));

    useCase.execute();

    assertThat(store.startedIds()).isEmpty();
  }

  @Test
  void should_mark_only_published_messages_as_started_when_half_claim_ttl_elapsed() {
    OutboxMessage<String> first = claimed(0);
    OutboxMessage<String> second = claimed(0);
    OutboxMessage<String> third = claimed(0);
    store.willClaim(List.of(first, second, third));

    useCase.execute();

    assertThat(store.startedIds()).containsExactly(first.id(), second.id());
  }

  private static void assertReleased(OutboxMessage<String> message, int attempts, Instant at) {
    assertThat(message.status()).isEqualTo(OutboxStatus.PENDING);
    assertThat(message.attempts()).isEqualTo(attempts);
    assertThat(message.claimedAt()).isNull();
    assertThat(message.claimedBy()).isEqualTo(CLAIM_MARKER);
    assertThat(message.nextAttemptAt()).isEqualTo(at);
    assertThat(message.error()).isNull();
  }

  private static OutboxMessage<String> claimed(int attempts) {
    return OutboxMessage.<String>builder()
        .id(UUID.randomUUID())
        .origin("origin")
        .topic("events")
        .key("k")
        .message("{}")
        .status(OutboxStatus.IN_FLIGHT)
        .attempts(attempts)
        .claimedAt(START)
        .claimedBy(CLAIM_MARKER)
        .build();
  }
}
