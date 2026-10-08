package io.codehunters.messaging.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.codehunters.messaging.domain.model.OutboxMessage;
import io.codehunters.messaging.domain.model.OutboxStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;

class SendOutboxMessagesUseCaseTest {

  private static final int BATCH_SIZE = 10;
  private static final int MAX_ATTEMPTS = 3;
  private static final String INSTANCE_ID = "instance-1";
  private static final String ORIGIN = "vitxo-ms-test";
  private static final long CLAIM_TTL_MS = 30_000L;
  private static final long BASE_BACKOFF_MS = 100L;
  private static final long MAX_BACKOFF_MS = 60_000L;
  private static final long PRODUCTION_BASE_BACKOFF_MS = 30_000L;
  private static final long PRODUCTION_MAX_BACKOFF_MS = 3_600_000L;
  private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final String CLAIM_MARKER = "instance-1:4f1c";
  private static final String PII_SENTINEL = "PII-SENTINEL-4242 holder Juan";

  private OutboxRepositoryPort repo;
  private MessagePublisherPort publisher;
  private SendOutboxMessagesUseCase useCase;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    repo = mock(OutboxRepositoryPort.class);
    publisher = mock(MessagePublisherPort.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    useCaseLogger().addAppender(logAppender);
    when(repo.markSendStarted(any())).thenReturn(true);
    useCase =
        new SendOutboxMessagesUseCase(
            repo,
            publisher,
            BATCH_SIZE,
            MAX_ATTEMPTS,
            INSTANCE_ID,
            ORIGIN,
            CLAIM_TTL_MS,
            BASE_BACKOFF_MS,
            MAX_BACKOFF_MS);
  }

  @AfterEach
  void tearDown() {
    useCaseLogger().detachAppender(logAppender);
  }

  private static Logger useCaseLogger() {
    return (Logger) LoggerFactory.getLogger(SendOutboxMessagesUseCase.class);
  }

  private SendOutboxMessagesUseCase useCaseWithFixedClock() {
    return new SendOutboxMessagesUseCase(
        repo,
        publisher,
        BATCH_SIZE,
        MAX_ATTEMPTS,
        INSTANCE_ID,
        ORIGIN,
        CLAIM_TTL_MS,
        PRODUCTION_BASE_BACKOFF_MS,
        PRODUCTION_MAX_BACKOFF_MS,
        FIXED_CLOCK);
  }

  private OutboxMessage<String> pending(String routingKey) {
    return OutboxMessage.<String>builder()
        .id(UUID.randomUUID())
        .origin(ORIGIN)
        .topic("events.test")
        .routingKey(routingKey)
        .message("payload")
        .build();
  }

  private OutboxMessage<String> claimed() {
    var msg = pending("k");
    msg.setStatus(OutboxStatus.IN_FLIGHT);
    msg.setClaimedBy(CLAIM_MARKER);
    return msg;
  }

  @Test
  void should_keep_claim_marker_for_fenced_update_when_published() {
    OutboxMessage<String> msg = claimed();
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of(msg));

    useCase.execute();

    assertThat(msg.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(msg.getClaimedBy()).isEqualTo(CLAIM_MARKER);
    verify(repo).update(msg);
  }

  @Test
  void should_keep_claim_marker_for_fenced_update_when_retry_scheduled() {
    OutboxMessage<String> msg = claimed();
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of(msg));
    doThrow(new RuntimeException("boom")).when(publisher).publish(any());

    useCase.execute();

    assertThat(msg.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(msg.getClaimedBy()).isEqualTo(CLAIM_MARKER);
    verify(repo).update(msg);
  }

  @Test
  void should_keep_claim_marker_for_fenced_update_when_marked_failed() {
    OutboxMessage<String> msg = claimed();
    msg.setAttempts(MAX_ATTEMPTS - 1);
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of(msg));
    doThrow(new RuntimeException("boom")).when(publisher).publish(any());

    useCase.execute();

    assertThat(msg.getStatus()).isEqualTo(OutboxStatus.FAILED);
    assertThat(msg.getClaimedBy()).isEqualTo(CLAIM_MARKER);
    verify(repo).update(msg);
  }

  @Test
  void should_publish_whole_message_when_claimed_and_mark_published() {
    OutboxMessage<String> msg = pending("user.created");
    when(repo.claimPendingMessages(eq(ORIGIN), anyInt(), eq(INSTANCE_ID), any(Duration.class)))
        .thenReturn(List.of(msg));

    useCase.execute();

    verify(publisher).publish(msg);
    verify(publisher, never()).publish(anyString(), anyString(), anyString());
    verify(publisher, never()).publish(anyString(), anyString());
    assertThat(msg.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(msg.getPublishedAt()).isNotNull();
    assertThat(msg.getError()).isNull();
    verify(repo).update(msg);
  }

  @Test
  @DisplayName("should_set_PENDING_and_schedule_next_attempt_when_publish_fails_under_limit")
  void should_schedule_retry_on_failure_under_limit() {
    OutboxMessage<String> msg = pending("k");
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of(msg));
    doThrow(new RuntimeException("boom")).when(publisher).publish(any());

    useCase.execute();

    assertThat(msg.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(msg.getAttempts()).isEqualTo(1);
    assertThat(msg.getError()).isEqualTo("java.lang.RuntimeException");
    assertThat(msg.getNextAttemptAt()).isNotNull();
  }

  @Test
  @DisplayName("should_mark_FAILED_when_attempts_reach_max")
  void should_mark_failed_at_max_attempts() {
    OutboxMessage<String> msg = pending("k");
    msg.setAttempts(MAX_ATTEMPTS - 1);
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of(msg));
    doThrow(new RuntimeException("boom")).when(publisher).publish(any());

    useCase.execute();

    assertThat(msg.getStatus()).isEqualTo(OutboxStatus.FAILED);
    assertThat(msg.getAttempts()).isEqualTo(MAX_ATTEMPTS);
    assertThat(msg.getNextAttemptAt()).isNull();
  }

  @Test
  @DisplayName("should_skip_processing_when_no_messages_claimed")
  void should_skip_when_empty() {
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of());

    useCase.execute();

    verify(publisher, never()).publish(any());
    verify(repo, never()).update(any());
  }

  @Test
  @DisplayName("should_double_backoff_with_attempts_when_not_capped")
  void should_compute_backoff_doubling() {
    assertThat(useCase.computeBackoffMs(1)).isEqualTo(BASE_BACKOFF_MS);
    assertThat(useCase.computeBackoffMs(2)).isEqualTo(BASE_BACKOFF_MS * 2);
    assertThat(useCase.computeBackoffMs(3)).isEqualTo(BASE_BACKOFF_MS * 4);
  }

  @Test
  @DisplayName("should_cap_backoff_at_max_when_attempts_large")
  void should_cap_backoff_at_max() {
    assertThat(useCase.computeBackoffMs(30)).isEqualTo(MAX_BACKOFF_MS);
    assertThat(useCase.computeBackoffMs(100)).isEqualTo(MAX_BACKOFF_MS);
  }

  @Test
  void should_store_exception_class_name_only_when_publish_fails() {
    OutboxMessage<String> msg = pending("k");
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of(msg));
    doThrow(new IllegalStateException(PII_SENTINEL)).when(publisher).publish(any());

    useCase.execute();

    assertThat(msg.getError())
        .isEqualTo("java.lang.IllegalStateException")
        .doesNotContain("PII-SENTINEL");
  }

  @Test
  void should_log_exception_class_name_without_message_or_throwable_when_publish_fails() {
    OutboxMessage<String> msg = pending("k");
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of(msg));
    doThrow(new IllegalStateException(PII_SENTINEL)).when(publisher).publish(any());

    useCase.execute();

    assertThat(logAppender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(line -> line.contains("java.lang.IllegalStateException"))
        .noneMatch(line -> line.contains("PII-SENTINEL"));
    assertThat(logAppender.list).allMatch(event -> event.getThrowableProxy() == null);
  }

  @Test
  void should_schedule_retry_from_injected_clock_when_publish_fails() {
    OutboxMessage<String> msg = pending("k");
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of(msg));
    doThrow(new RuntimeException("boom")).when(publisher).publish(any());

    useCaseWithFixedClock().execute();

    assertThat(msg.getNextAttemptAt()).isEqualTo(LocalDateTime.of(2026, 10, 5, 10, 0, 30));
  }

  @Test
  void should_stamp_publish_time_from_injected_clock_when_publish_succeeds() {
    OutboxMessage<String> msg = pending("k");
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of(msg));

    useCaseWithFixedClock().execute();

    assertThat(msg.getPublishedAt()).isEqualTo(LocalDateTime.of(2026, 10, 5, 10, 0, 0));
  }

  @Test
  void should_mark_send_started_before_publishing_when_message_is_dispatched() {
    OutboxMessage<String> msg = claimed();
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of(msg));

    useCase.execute();

    InOrder order = inOrder(repo, publisher);
    order.verify(repo).markSendStarted(msg);
    order.verify(publisher).publish(msg);
    order.verify(repo).update(msg);
  }

  @Test
  void should_neither_publish_nor_update_when_claim_was_lost_before_send() {
    OutboxMessage<String> msg = claimed();
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of(msg));
    when(repo.markSendStarted(msg)).thenReturn(false);

    useCase.execute();

    verify(publisher, never()).publish(any());
    verify(repo, never()).update(any());
    assertThat(msg.getStatus()).isEqualTo(OutboxStatus.IN_FLIGHT);
  }

  @Test
  void should_keep_dispatching_next_message_when_claim_of_previous_was_lost() {
    OutboxMessage<String> lost = claimed();
    OutboxMessage<String> held = claimed();
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of(lost, held));
    when(repo.markSendStarted(lost)).thenReturn(false);

    useCase.execute();

    verify(publisher).publish(held);
    verify(repo).update(held);
    assertThat(held.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
  }

  @Test
  void should_neither_publish_nor_update_when_send_mark_fails() {
    OutboxMessage<String> msg = claimed();
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of(msg));
    when(repo.markSendStarted(msg)).thenThrow(new IllegalStateException(PII_SENTINEL));

    useCase.execute();

    verify(publisher, never()).publish(any());
    verify(repo, never()).update(any());
    assertThat(msg.getStatus()).isEqualTo(OutboxStatus.IN_FLIGHT);
    assertThat(msg.getAttempts()).isZero();
  }

  @Test
  void should_keep_dispatching_next_message_when_send_mark_of_previous_fails() {
    OutboxMessage<String> broken = claimed();
    OutboxMessage<String> held = claimed();
    when(repo.claimPendingMessages(any(), anyInt(), any(), any()))
        .thenReturn(List.of(broken, held));
    when(repo.markSendStarted(broken)).thenThrow(new IllegalStateException(PII_SENTINEL));

    useCase.execute();

    verify(publisher).publish(held);
    verify(repo).update(held);
    assertThat(held.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
  }

  @Test
  void should_log_message_id_without_exception_message_when_send_mark_fails() {
    OutboxMessage<String> msg = claimed();
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of(msg));
    when(repo.markSendStarted(msg)).thenThrow(new IllegalStateException(PII_SENTINEL));

    useCase.execute();

    assertThat(logAppender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(line -> line.contains(msg.getId().toString()))
        .noneMatch(line -> line.contains("PII-SENTINEL"));
    assertThat(logAppender.list).allMatch(event -> event.getThrowableProxy() == null);
  }
}
