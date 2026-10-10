package io.codehunters.messaging.jdbc.scheduler;

import java.time.Duration;

/**
 * The ShedLock lock one outbox task runs under, so only one instance runs it at a time.
 *
 * @param name the lock name, shared by every instance of the application
 * @param atMost how long a node may hold the lock if it never releases it
 * @param atLeast how long a node keeps the lock after a run, even a short one
 */
public record OutboxTaskLock(String name, Duration atMost, Duration atLeast) {}
