package io.codehunters.messaging.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.slf4j.LoggerFactory;

class SendOutboxMessagesUseCaseTest {

  private static final int MAX_ATTEMPTS = 3;
  private static final String ORIGIN = "orders-ms-test";
  private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
  private static final ClaimRequest CLAIM =
      new ClaimRequest(ORIGIN, "instance-1", 10, Duration.ofSeconds(30));
  private static final RetryPolicy RETRY =
      new RetryPolicy(MAX_ATTEMPTS, Duration.ofSeconds(30), Duration.ofHours(1));
  private static final String CLAIM_MARKER = "instance-1:4f1c";
  private static final String PII_SENTINEL = "PII-SENTINEL-4242 holder Juan";

  private List<String> calls;
  private FakeDispatchStore store;
  private RecordingPublisher publisher;
  private SendOutboxMessagesUseCase useCase;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    MutableClock clock = new MutableClock(NOW);
    calls = new ArrayList<>();
    store = new FakeDispatchStore(clock, calls);
    publisher = new RecordingPublisher(clock, calls);
    useCase =
        new SendOutboxMessagesUseCase(store, publisher, new DispatchSettings(CLAIM, RETRY, clock));
    logAppender = new ListAppender<>();
    logAppender.start();
    useCaseLogger().addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    useCaseLogger().detachAppender(logAppender);
  }

  @Test
  void should_claim_with_configured_request_when_executed() {
    useCase.execute();

    assertThat(store.claimRequests()).containsExactly(CLAIM);
  }

  @Test
  void should_keep_claim_marker_for_fenced_update_when_published() {
    store.willClaim(List.of(claimed(0)));

    useCase.execute();

    assertThat(onlyUpdate().status()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(onlyUpdate().claimedBy()).isEqualTo(CLAIM_MARKER);
  }

  @Test
  void should_keep_claim_marker_for_fenced_update_when_retry_scheduled() {
    store.willClaim(List.of(claimed(0)));
    publisher.failWith(new RuntimeException("boom"));

    useCase.execute();

    assertThat(onlyUpdate().status()).isEqualTo(OutboxStatus.PENDING);
    assertThat(onlyUpdate().claimedBy()).isEqualTo(CLAIM_MARKER);
  }

  @Test
  void should_keep_claim_marker_for_fenced_update_when_marked_failed() {
    store.willClaim(List.of(claimed(MAX_ATTEMPTS - 1)));
    publisher.failWith(new RuntimeException("boom"));

    useCase.execute();

    assertThat(onlyUpdate().status()).isEqualTo(OutboxStatus.FAILED);
    assertThat(onlyUpdate().claimedBy()).isEqualTo(CLAIM_MARKER);
  }

  @Test
  void should_publish_whole_message_and_mark_published_when_claimed() {
    OutboxMessage<String> message = claimed(0);
    store.willClaim(List.of(message));

    useCase.execute();

    assertThat(publisher.publishedIds()).containsExactly(message.id());
    assertThat(onlyUpdate().status()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(onlyUpdate().publishedAt()).isEqualTo(NOW);
    assertThat(onlyUpdate().error()).isNull();
  }

  @Test
  void should_set_pending_and_schedule_next_attempt_when_publish_fails_under_limit() {
    store.willClaim(List.of(claimed(0)));
    publisher.failWith(new RuntimeException("boom"));

    useCase.execute();

    assertThat(onlyUpdate().status()).isEqualTo(OutboxStatus.PENDING);
    assertThat(onlyUpdate().attempts()).isEqualTo(1);
    assertThat(onlyUpdate().error()).isEqualTo("java.lang.RuntimeException");
  }

  @Test
  void should_schedule_retry_from_clock_and_backoff_when_publish_fails() {
    store.willClaim(List.of(claimed(0)));
    publisher.failWith(new RuntimeException("boom"));

    useCase.execute();

    assertThat(onlyUpdate().nextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
  }

  @Test
  void should_mark_failed_without_next_attempt_when_attempts_reach_max() {
    store.willClaim(List.of(claimed(MAX_ATTEMPTS - 1)));
    publisher.failWith(new RuntimeException("boom"));

    useCase.execute();

    assertThat(onlyUpdate().status()).isEqualTo(OutboxStatus.FAILED);
    assertThat(onlyUpdate().attempts()).isEqualTo(MAX_ATTEMPTS);
    assertThat(onlyUpdate().nextAttemptAt()).isNull();
  }

  @Test
  void should_neither_publish_nor_update_when_no_messages_claimed() {
    useCase.execute();

    assertThat(publisher.published()).isEmpty();
    assertThat(store.updated()).isEmpty();
  }

  @Test
  void should_store_exception_class_name_only_when_publish_fails() {
    store.willClaim(List.of(claimed(0)));
    publisher.failWith(new IllegalStateException(PII_SENTINEL));

    useCase.execute();

    assertThat(onlyUpdate().error())
        .isEqualTo("java.lang.IllegalStateException")
        .doesNotContain("PII-SENTINEL");
  }

  @Test
  void should_log_exception_class_name_without_message_or_throwable_when_publish_fails() {
    store.willClaim(List.of(claimed(0)));
    publisher.failWith(new IllegalStateException(PII_SENTINEL));

    useCase.execute();

    assertThat(logAppender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(line -> line.contains("java.lang.IllegalStateException"))
        .noneMatch(line -> line.contains("PII-SENTINEL"));
    assertThat(logAppender.list).allMatch(event -> event.getThrowableProxy() == null);
  }

  @Test
  void should_mark_send_started_then_publish_then_update_when_message_is_dispatched() {
    OutboxMessage<String> message = claimed(0);
    store.willClaim(List.of(message));

    useCase.execute();

    assertThat(calls)
        .containsExactly(
            "mark " + message.id(), "publish " + message.id(), "update " + message.id());
  }

  @Test
  void should_publish_marked_message_when_send_mark_is_written() {
    store.willClaim(List.of(claimed(0)));

    useCase.execute();

    assertThat(publisher.published().getFirst().sendStartedAt()).isEqualTo(NOW);
  }

  @Test
  void should_neither_publish_nor_update_when_claim_was_lost_before_send() {
    OutboxMessage<String> message = claimed(0);
    store.willClaim(List.of(message));
    store.refuseMark(message);

    useCase.execute();

    assertThat(publisher.published()).isEmpty();
    assertThat(store.updated()).isEmpty();
  }

  @Test
  void should_keep_dispatching_next_message_when_claim_of_previous_was_lost() {
    OutboxMessage<String> lost = claimed(0);
    OutboxMessage<String> held = claimed(0);
    store.willClaim(List.of(lost, held));
    store.refuseMark(lost);

    useCase.execute();

    assertThat(publisher.publishedIds()).containsExactly(held.id());
    assertThat(store.updatedIds()).containsExactly(held.id());
  }

  @Test
  void should_neither_publish_nor_update_when_send_mark_fails() {
    OutboxMessage<String> message = claimed(0);
    store.willClaim(List.of(message));
    store.failMark(message, new IllegalStateException(PII_SENTINEL));

    useCase.execute();

    assertThat(publisher.published()).isEmpty();
    assertThat(store.updated()).isEmpty();
  }

  @Test
  void should_keep_dispatching_next_message_when_send_mark_of_previous_fails() {
    OutboxMessage<String> broken = claimed(0);
    OutboxMessage<String> held = claimed(0);
    store.willClaim(List.of(broken, held));
    store.failMark(broken, new IllegalStateException(PII_SENTINEL));

    useCase.execute();

    assertThat(publisher.publishedIds()).containsExactly(held.id());
    assertThat(onlyUpdate().status()).isEqualTo(OutboxStatus.PUBLISHED);
  }

  @Test
  void should_log_message_id_without_exception_message_when_send_mark_fails() {
    OutboxMessage<String> message = claimed(0);
    store.willClaim(List.of(message));
    store.failMark(message, new IllegalStateException(PII_SENTINEL));

    useCase.execute();

    assertThat(logAppender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(line -> line.contains(message.id().toString()))
        .noneMatch(line -> line.contains("PII-SENTINEL"));
    assertThat(logAppender.list).allMatch(event -> event.getThrowableProxy() == null);
  }

  @Test
  void should_log_message_id_when_claim_was_lost_before_send() {
    OutboxMessage<String> message = claimed(0);
    store.willClaim(List.of(message));
    store.refuseMark(message);

    useCase.execute();

    assertThat(logAppender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(line -> line.contains(message.id().toString()));
  }

  private OutboxMessage<String> onlyUpdate() {
    assertThat(store.updated()).hasSize(1);
    return store.updated().getFirst();
  }

  private static OutboxMessage<String> claimed(int attempts) {
    return OutboxMessage.<String>builder()
        .id(UUID.randomUUID())
        .origin(ORIGIN)
        .topic("events.test")
        .routingKey("k")
        .message("payload")
        .status(OutboxStatus.IN_FLIGHT)
        .attempts(attempts)
        .claimedAt(NOW)
        .claimedBy(CLAIM_MARKER)
        .build();
  }

  private static Logger useCaseLogger() {
    return (Logger) LoggerFactory.getLogger(SendOutboxMessagesUseCase.class);
  }
}
