/**
 * Outbox dispatch and purge tasks, run on the SDK's own task scheduler, each under a ShedLock lock.
 */
package io.codehunters.messaging.jdbc.scheduler;
