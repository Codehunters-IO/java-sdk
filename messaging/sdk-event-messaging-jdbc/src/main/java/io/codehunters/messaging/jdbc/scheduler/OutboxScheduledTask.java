package io.codehunters.messaging.jdbc.scheduler;

/**
 * An outbox task with its schedule and the ShedLock lock each run takes.
 *
 * @param action the work of one run, such as {@link OutboxScheduler} or {@link
 *     OutboxPurgeScheduler}
 * @param schedule when the task runs
 * @param lock the lock each run is wrapped in
 */
public record OutboxScheduledTask(
    Runnable action, OutboxTaskSchedule schedule, OutboxTaskLock lock) {}
