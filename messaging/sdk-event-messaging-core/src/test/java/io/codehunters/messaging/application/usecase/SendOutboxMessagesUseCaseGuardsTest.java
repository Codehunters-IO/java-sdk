package io.codehunters.messaging.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.codehunters.messaging.domain.model.OutboxMessage;
import io.codehunters.messaging.domain.model.OutboxStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SendOutboxMessagesUseCaseGuardsTest {

  private static final int MAX_ATTEMPTS = 3;
  private static final long CLAIM_TTL_MS = 10_000L;
  private static final Instant START = Instant.parse("2026-10-05T10:00:00Z");
  private static final Duration PUBLISH_TIME = Duration.ofSeconds(3);
  private static final String CLAIM_MARKER = "instance-1:marker";

  private MutableClock clock;
  private List<String> calls;
  private FakeRepository repository;
  private SlowPublisher publisher;
  private SendOutboxMessagesUseCase useCase;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(START);
    calls = new ArrayList<>();
    repository = new FakeRepository(clock, calls);
    publisher = new SlowPublisher(clock, PUBLISH_TIME, calls);
    useCase =
        new SendOutboxMessagesUseCase(
            repository,
            publisher,
            10,
            MAX_ATTEMPTS,
            "instance-1",
            "origin",
            CLAIM_TTL_MS,
            1_000L,
            60_000L,
            clock);
  }

  @Test
  void should_mark_failed_without_publishing_when_reclaimed_message_reached_max_attempts() {
    OutboxMessage<String> exhausted = claimed(MAX_ATTEMPTS);
    repository.willClaim(List.of(exhausted));

    useCase.execute();

    assertThat(publisher.published).isEmpty();
    assertThat(exhausted.getStatus()).isEqualTo(OutboxStatus.FAILED);
    assertThat(exhausted.getError()).isEqualTo("max-attempts-exceeded");
    assertThat(exhausted.getAttempts()).isEqualTo(MAX_ATTEMPTS);
    assertThat(exhausted.getNextAttemptAt()).isNull();
    assertThat(repository.updated).containsExactly(exhausted);
  }

  @Test
  void should_mark_failed_without_publishing_when_reclaimed_message_exceeded_max_attempts() {
    OutboxMessage<String> exhausted = claimed(MAX_ATTEMPTS + 2);
    repository.willClaim(List.of(exhausted));

    useCase.execute();

    assertThat(publisher.published).isEmpty();
    assertThat(exhausted.getStatus()).isEqualTo(OutboxStatus.FAILED);
  }

  @Test
  void should_publish_message_when_attempts_below_max() {
    OutboxMessage<String> retried = claimed(MAX_ATTEMPTS - 1);
    repository.willClaim(List.of(retried));

    useCase.execute();

    assertThat(publisher.published).containsExactly(retried);
    assertThat(retried.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
  }

  @Test
  void should_release_remaining_messages_when_half_claim_ttl_elapsed() {
    OutboxMessage<String> first = claimed(0);
    OutboxMessage<String> second = claimed(0);
    OutboxMessage<String> third = claimed(1);
    OutboxMessage<String> fourth = claimed(0);
    repository.willClaim(List.of(first, second, third, fourth));

    useCase.execute();

    assertThat(publisher.published).containsExactly(first, second);
    assertThat(repository.updated).containsExactly(first, second, third, fourth);
    assertReleased(third, 1, START.plus(PUBLISH_TIME.multipliedBy(2)));
    assertReleased(fourth, 0, START.plus(PUBLISH_TIME.multipliedBy(2)));
  }

  @Test
  void should_count_claim_time_against_budget_when_claim_is_slow() {
    OutboxMessage<String> only = claimed(0);
    repository.willClaim(List.of(only));
    repository.claimTakes(Duration.ofMillis(CLAIM_TTL_MS / 2));

    useCase.execute();

    assertThat(publisher.published).isEmpty();
    assertReleased(only, 0, START.plusMillis(CLAIM_TTL_MS / 2));
  }

  @Test
  void should_not_mark_send_started_when_reclaimed_message_reached_max_attempts() {
    OutboxMessage<String> exhausted = claimed(MAX_ATTEMPTS);
    repository.willClaim(List.of(exhausted));

    useCase.execute();

    assertThat(repository.started).isEmpty();
  }

  @Test
  void should_mark_only_published_messages_as_started_when_half_claim_ttl_elapsed() {
    OutboxMessage<String> first = claimed(0);
    OutboxMessage<String> second = claimed(0);
    OutboxMessage<String> third = claimed(0);
    repository.willClaim(List.of(first, second, third));

    useCase.execute();

    assertThat(repository.started).containsExactly(first, second);
  }

  @Test
  void should_mark_send_started_before_publisher_is_called_when_message_is_dispatched() {
    OutboxMessage<String> only = claimed(0);
    repository.willClaim(List.of(only));

    useCase.execute();

    assertThat(calls).containsExactly("mark " + only.getId(), "publish " + only.getId());
  }

  private static void assertReleased(OutboxMessage<String> message, int attempts, Instant at) {
    assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(message.getAttempts()).isEqualTo(attempts);
    assertThat(message.getClaimedAt()).isNull();
    assertThat(message.getClaimedBy()).isEqualTo(CLAIM_MARKER);
    assertThat(message.getNextAttemptAt()).isEqualTo(LocalDateTime.ofInstant(at, ZoneOffset.UTC));
    assertThat(message.getError()).isNull();
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
        .claimedAt(LocalDateTime.ofInstant(START, ZoneOffset.UTC))
        .claimedBy(CLAIM_MARKER)
        .build();
  }

  /** Clock the fakes move forward to simulate slow claims and publishes. */
  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  /** Repository that hands out a prepared batch and records updates in order. */
  private static final class FakeRepository implements OutboxRepositoryPort {
    private final MutableClock clock;
    private final List<String> calls;
    private final List<OutboxMessage<String>> updated = new ArrayList<>();
    private final List<OutboxMessage<String>> started = new ArrayList<>();
    private List<OutboxMessage<String>> toClaim = List.of();
    private Duration claimDuration = Duration.ZERO;

    FakeRepository(MutableClock clock, List<String> calls) {
      this.clock = clock;
      this.calls = calls;
    }

    void willClaim(List<OutboxMessage<String>> messages) {
      toClaim = messages;
    }

    void claimTakes(Duration duration) {
      claimDuration = duration;
    }

    @Override
    public <T> void save(OutboxMessage<T> outboxMessage) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<OutboxMessage<String>> claimPendingMessages(
        String origin, int batchSize, String claimedBy, Duration claimTtl) {
      clock.advance(claimDuration);
      return toClaim;
    }

    @Override
    public boolean markSendStarted(OutboxMessage<String> outboxMessage) {
      started.add(outboxMessage);
      calls.add("mark " + outboxMessage.getId());
      return true;
    }

    @Override
    public void update(OutboxMessage<String> outboxMessage) {
      updated.add(outboxMessage);
    }

    @Override
    public int purgePublished(LocalDateTime cutoff, int batchSize) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long countFailed() {
      throw new UnsupportedOperationException();
    }
  }

  /** Publisher whose every send takes a fixed time on the shared clock. */
  private static final class SlowPublisher implements MessagePublisherPort {
    private final MutableClock clock;
    private final Duration publishTime;
    private final List<OutboxMessage<String>> published = new ArrayList<>();
    private final List<String> calls;

    SlowPublisher(MutableClock clock, Duration publishTime, List<String> calls) {
      this.clock = clock;
      this.publishTime = publishTime;
      this.calls = calls;
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
      calls.add("publish " + message.getId());
      published.add(message);
      clock.advance(publishTime);
    }
  }
}
