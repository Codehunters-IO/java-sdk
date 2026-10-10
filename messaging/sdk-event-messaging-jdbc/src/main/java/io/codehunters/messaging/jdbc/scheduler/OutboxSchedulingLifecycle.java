package io.codehunters.messaging.jdbc.scheduler;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;

/**
 * Registers the outbox tasks on the SDK's own {@link TaskScheduler} when the application context
 * starts and cancels them when it stops. Each run is wrapped in its ShedLock lock, so only one
 * instance runs a task at a time. No application-wide scheduling ({@code @EnableScheduling}) is
 * turned on, and the application's own scheduler is never used. A stop hook, such as stopping the
 * commit-time dispatch trigger, runs when the lifecycle stops, before the scheduler shuts down.
 */
public final class OutboxSchedulingLifecycle implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(OutboxSchedulingLifecycle.class);

  private final TaskScheduler scheduler;
  private final LockingTaskExecutor executor;
  private final List<OutboxScheduledTask> tasks;
  private final Runnable stopHook;
  private volatile List<ScheduledFuture<?>> scheduled = List.of();

  /**
   * Creates the lifecycle.
   *
   * @param scheduler the scheduler the tasks run on
   * @param lockProvider the ShedLock provider that coordinates the instances
   * @param tasks the tasks to schedule
   */
  public OutboxSchedulingLifecycle(
      TaskScheduler scheduler, LockProvider lockProvider, List<OutboxScheduledTask> tasks) {
    this(scheduler, new DefaultLockingTaskExecutor(lockProvider), List.copyOf(tasks), () -> {});
  }

  private OutboxSchedulingLifecycle(
      TaskScheduler scheduler,
      LockingTaskExecutor executor,
      List<OutboxScheduledTask> tasks,
      Runnable stopHook) {
    this.scheduler = scheduler;
    this.executor = executor;
    this.tasks = tasks;
    this.stopHook = stopHook;
  }

  /**
   * Returns a copy of this lifecycle that also runs {@code hook} when it stops; this one is left
   * unchanged.
   *
   * @param hook what to run on stop, after the tasks are cancelled
   * @return the new lifecycle
   */
  public OutboxSchedulingLifecycle onStop(Runnable hook) {
    return new OutboxSchedulingLifecycle(scheduler, executor, tasks, hook);
  }

  /** Schedules every task with its initial and fixed delay. */
  @Override
  public void start() {
    Instant now = scheduler.getClock().instant();
    scheduled = tasks.stream().<ScheduledFuture<?>>map(task -> schedule(task, now)).toList();
  }

  /** Cancels the scheduled tasks and runs the stop hook; a run in progress finishes. */
  @Override
  public void stop() {
    scheduled.forEach(future -> future.cancel(false));
    scheduled = List.of();
    stopHook.run();
  }

  /**
   * Reports whether the tasks are scheduled.
   *
   * @return {@code true} between {@link #start()} and {@link #stop()}
   */
  @Override
  public boolean isRunning() {
    return !scheduled.isEmpty();
  }

  private ScheduledFuture<?> schedule(OutboxScheduledTask task, Instant now) {
    Instant first = now.plus(task.schedule().initialDelay());
    return scheduler.scheduleWithFixedDelay(
        () -> runLocked(task), first, task.schedule().fixedDelay());
  }

  // The tasks swallow their own failures; this catches the lock provider's, such as an
  // unreachable database, logging the class name only so no data leaks into the log.
  private void runLocked(OutboxScheduledTask task) {
    try {
      executor.executeWithLock(task.action(), lockConfiguration(task.lock()));
    } catch (RuntimeException e) {
      log.error("[ERROR] - outbox task {} failed: {}", task.lock().name(), e.getClass().getName());
    }
  }

  private static LockConfiguration lockConfiguration(OutboxTaskLock lock) {
    return new LockConfiguration(ClockProvider.now(), lock.name(), lock.atMost(), lock.atLeast());
  }
}
