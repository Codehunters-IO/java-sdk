package io.codehunters.messaging.jdbc.scheduler;

import java.time.Duration;

/**
 * When an outbox task runs: once after the initial delay, then again a fixed delay after each run
 * ends.
 *
 * @param initialDelay the delay between the start of the scheduler and the first run
 * @param fixedDelay the delay between the end of one run and the start of the next
 */
public record OutboxTaskSchedule(Duration initialDelay, Duration fixedDelay) {}
