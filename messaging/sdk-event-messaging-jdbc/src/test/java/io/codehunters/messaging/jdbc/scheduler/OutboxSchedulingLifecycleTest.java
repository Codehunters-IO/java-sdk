package io.codehunters.messaging.jdbc.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

class OutboxSchedulingLifecycleTest {

  private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
  private static final Duration INITIAL_DELAY = Duration.ofMinutes(1);
  private static final Duration FIXED_DELAY = Duration.ofSeconds(5);
  private static final OutboxTaskLock LOCK =
      new OutboxTaskLock("outbox-app", Duration.ofMinutes(5), Duration.ZERO);

  private final TaskScheduler scheduler = mock(TaskScheduler.class);
  private final ScheduledFuture<?> future = mock(ScheduledFuture.class);
  private final AtomicInteger runs = new AtomicInteger();
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    when(scheduler.getClock()).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));
    when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Instant.class), any()))
        .thenAnswer(invocation -> future);
    logAppender = new ListAppender<>();
    logAppender.start();
    lifecycleLogger().addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    lifecycleLogger().detachAppender(logAppender);
  }

  @Test
  void should_schedule_task_with_its_delays_when_started() {
    lifecycle(RecordingLockProvider.granting()).start();

    verify(scheduler)
        .scheduleWithFixedDelay(any(Runnable.class), eq(NOW.plus(INITIAL_DELAY)), eq(FIXED_DELAY));
  }

  @Test
  void should_report_running_when_started() {
    OutboxSchedulingLifecycle lifecycle = lifecycle(RecordingLockProvider.granting());

    lifecycle.start();

    assertThat(lifecycle.isRunning()).isTrue();
  }

  @Test
  void should_run_task_under_its_shedlock_lock_when_triggered() {
    RecordingLockProvider locks = RecordingLockProvider.granting();
    lifecycle(locks).start();

    scheduledRunnable().run();

    assertThat(runs).hasValue(1);
    assertThat(locks.unlocks()).isEqualTo(1);
    assertThat(locks.requests())
        .singleElement()
        .extracting(
            LockConfiguration::getName,
            LockConfiguration::getLockAtMostFor,
            LockConfiguration::getLockAtLeastFor)
        .containsExactly("outbox-app", Duration.ofMinutes(5), Duration.ZERO);
  }

  @Test
  void should_skip_task_when_lock_is_held_by_another_instance() {
    lifecycle(RecordingLockProvider.held()).start();

    scheduledRunnable().run();

    assertThat(runs).hasValue(0);
  }

  @Test
  void should_not_propagate_and_log_class_name_only_when_lock_provider_fails() {
    lifecycle(failingLockProvider()).start();
    Runnable task = scheduledRunnable();

    assertDoesNotThrow(task::run);

    assertThat(logAppender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anyMatch(line -> line.contains("java.lang.IllegalStateException"))
        .noneMatch(line -> line.contains("PII-SENTINEL"));
    assertThat(logAppender.list).allMatch(event -> event.getThrowableProxy() == null);
  }

  @Test
  void should_cancel_scheduled_tasks_when_stopped() {
    OutboxSchedulingLifecycle lifecycle = lifecycle(RecordingLockProvider.granting());
    lifecycle.start();

    lifecycle.stop();

    verify(future).cancel(false);
    assertThat(lifecycle.isRunning()).isFalse();
  }

  @Test
  void should_run_stop_hook_when_stopped() {
    AtomicInteger stops = new AtomicInteger();
    OutboxSchedulingLifecycle lifecycle =
        lifecycle(RecordingLockProvider.granting()).onStop(stops::incrementAndGet);
    lifecycle.start();

    lifecycle.stop();

    assertThat(stops).hasValue(1);
  }

  @Test
  void should_keep_original_without_hook_when_stop_hook_is_added() {
    AtomicInteger stops = new AtomicInteger();
    OutboxSchedulingLifecycle original = lifecycle(RecordingLockProvider.granting());
    original.onStop(stops::incrementAndGet);
    original.start();

    original.stop();

    assertThat(stops).hasValue(0);
  }

  private OutboxSchedulingLifecycle lifecycle(LockProvider locks) {
    OutboxScheduledTask task =
        new OutboxScheduledTask(
            runs::incrementAndGet, new OutboxTaskSchedule(INITIAL_DELAY, FIXED_DELAY), LOCK);
    return new OutboxSchedulingLifecycle(scheduler, locks, List.of(task));
  }

  private Runnable scheduledRunnable() {
    ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler).scheduleWithFixedDelay(captor.capture(), any(Instant.class), any());
    return captor.getValue();
  }

  private static LockProvider failingLockProvider() {
    return configuration -> {
      throw new IllegalStateException("PII-SENTINEL");
    };
  }

  private static Logger lifecycleLogger() {
    return (Logger) LoggerFactory.getLogger(OutboxSchedulingLifecycle.class);
  }
}
