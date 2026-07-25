package io.codehunters.messaging.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.codehunters.messaging.application.port.MessagePublisherPort;
import io.codehunters.messaging.application.port.OutboxRepositoryPort;
import io.codehunters.messaging.domain.model.OutboxMessage;
import io.codehunters.messaging.domain.model.OutboxStatus;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SendOutboxMessagesUseCaseTest {

  private static final int BATCH_SIZE = 10;
  private static final int MAX_ATTEMPTS = 3;
  private static final String INSTANCE_ID = "instance-1";
  private static final String ORIGIN = "vitxo-ms-test";
  private static final long CLAIM_TTL_MS = 30_000L;
  private static final long BASE_BACKOFF_MS = 100L;
  private static final long MAX_BACKOFF_MS = 60_000L;

  private OutboxRepositoryPort repo;
  private MessagePublisherPort publisher;
  private SendOutboxMessagesUseCase useCase;

  @BeforeEach
  void setUp() {
    repo = mock(OutboxRepositoryPort.class);
    publisher = mock(MessagePublisherPort.class);
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

  private OutboxMessage<String> pending(String routingKey) {
    return OutboxMessage.<String>builder()
        .id(UUID.randomUUID())
        .origin(ORIGIN)
        .topic("events.test")
        .routingKey(routingKey)
        .message("payload")
        .build();
  }

  @Test
  @DisplayName("should_publish_with_routing_key_when_present_and_mark_published")
  void should_publish_with_routing_key() {
    OutboxMessage<String> msg = pending("user.created");
    when(repo.claimPendingMessages(eq(ORIGIN), anyInt(), eq(INSTANCE_ID), any(Duration.class)))
        .thenReturn(List.of(msg));

    useCase.execute();

    verify(publisher).publish("events.test", "user.created", "payload");
    verify(publisher, never()).publish(anyString(), anyString());
    assertThat(msg.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    assertThat(msg.getPublishedAt()).isNotNull();
    assertThat(msg.getError()).isNull();
    verify(repo).update(msg);
  }

  @Test
  @DisplayName("should_publish_without_routing_key_when_blank")
  void should_publish_without_routing_key_when_blank() {
    OutboxMessage<String> msg = pending("   ");
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of(msg));

    useCase.execute();

    verify(publisher).publish("events.test", "payload");
  }

  @Test
  @DisplayName("should_publish_without_routing_key_when_null")
  void should_publish_without_routing_key_when_null() {
    OutboxMessage<String> msg = pending(null);
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of(msg));

    useCase.execute();

    verify(publisher).publish("events.test", "payload");
  }

  @Test
  @DisplayName("should_set_PENDING_and_schedule_next_attempt_when_publish_fails_under_limit")
  void should_schedule_retry_on_failure_under_limit() {
    OutboxMessage<String> msg = pending("k");
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of(msg));
    doThrow(new RuntimeException("boom")).when(publisher).publish(any(), any(), any());

    useCase.execute();

    assertThat(msg.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(msg.getAttempts()).isEqualTo(1);
    assertThat(msg.getError()).isEqualTo("boom");
    assertThat(msg.getNextAttemptAt()).isNotNull();
  }

  @Test
  @DisplayName("should_mark_FAILED_when_attempts_reach_max")
  void should_mark_failed_at_max_attempts() {
    OutboxMessage<String> msg = pending("k");
    msg.setAttempts(MAX_ATTEMPTS - 1);
    when(repo.claimPendingMessages(any(), anyInt(), any(), any())).thenReturn(List.of(msg));
    doThrow(new RuntimeException("boom")).when(publisher).publish(any(), any(), any());

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

    verify(publisher, never()).publish(any(), any(), any());
    verify(publisher, never()).publish(any(), any());
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
}
