package io.codehunters.messaging.jdbc.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.codehunters.messaging.OutboxRepositoryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class OutboxPurgeSchedulerTest {

  private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
  private static final Duration RETENTION = Duration.ofDays(7);
  private static final int BATCH = 500;
  private static final LocalDateTime EXPECTED_CUTOFF = LocalDateTime.of(2026, 9, 28, 12, 0);

  @Mock private OutboxRepositoryPort repository;

  private OutboxPurgeScheduler scheduler;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    var clock = Clock.fixed(NOW, ZoneOffset.UTC);
    scheduler =
        new OutboxPurgeScheduler(repository, new OutboxPurgeSettings(RETENTION, BATCH, clock));
    logAppender = new ListAppender<>();
    logAppender.start();
    schedulerLogger().addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    schedulerLogger().detachAppender(logAppender);
  }

  private static Logger schedulerLogger() {
    return (Logger) LoggerFactory.getLogger(OutboxPurgeScheduler.class);
  }

  @Test
  void should_loop_batches_with_retention_cutoff_when_batches_are_full() {
    when(repository.purgePublished(EXPECTED_CUTOFF, BATCH)).thenReturn(500, 500, 37);

    scheduler.purgePublished();

    verify(repository, times(3)).purgePublished(EXPECTED_CUTOFF, BATCH);
  }

  @Test
  void should_stop_after_one_call_when_first_batch_is_partial() {
    when(repository.purgePublished(EXPECTED_CUTOFF, BATCH)).thenReturn(0);

    scheduler.purgePublished();

    verify(repository, times(1)).purgePublished(any(), anyInt());
  }

  @Test
  void should_not_propagate_and_log_class_name_only_when_purge_fails() {
    when(repository.purgePublished(any(), anyInt()))
        .thenThrow(new RuntimeException("PII-SENTINEL"));

    assertDoesNotThrow(() -> scheduler.purgePublished());

    assertThat(logAppender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(line -> line.contains("java.lang.RuntimeException"))
        .noneMatch(line -> line.contains("PII-SENTINEL"));
    assertThat(logAppender.list).allMatch(event -> event.getThrowableProxy() == null);
  }
}
