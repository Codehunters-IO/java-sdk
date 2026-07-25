package io.codehunters.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the event-messaging SDK, bound from the {@code outbox} prefix.
 *
 * @param enabled whether the outbox SDK is active
 * @param batchSize the number of messages dispatched per scheduler tick
 * @param maxAttempts the maximum number of publish attempts before marking a message FAILED
 * @param task the scheduler/ShedLock configuration
 * @param claim the claim-lifetime configuration for stale-claim recovery
 * @param backoff the retry backoff configuration
 */
@ConfigurationProperties(prefix = "outbox")
public record EventMessagingProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("50") int batchSize,
    @DefaultValue("5") int maxAttempts,
    @DefaultValue Task task,
    @DefaultValue Claim claim,
    @DefaultValue Backoff backoff) {

  /**
   * Scheduler and ShedLock configuration.
   *
   * @param fixedDelay the fixed delay between scheduler ticks, in milliseconds
   * @param lockAtMost the maximum time a node may hold the dispatch lock
   * @param lockAtLeast the minimum time a node holds the dispatch lock before releasing it
   * @param lockName the ShedLock lock name; defaults to the current application name
   */
  public record Task(
      @DefaultValue("5000") String fixedDelay,
      @DefaultValue("60000") String lockAtMost,
      @DefaultValue("10000") String lockAtLeast,
      @DefaultValue("outbox-${spring.application.name:unknown}") String lockName) {}

  /**
   * Claim-lifetime configuration.
   *
   * @param ttlMs the time after which an in-flight claim is considered stale and reclaimable
   */
  public record Claim(@DefaultValue("300000") long ttlMs) {}

  /**
   * Retry backoff configuration.
   *
   * @param baseDelayMs the initial backoff delay in milliseconds (doubled on each retry)
   * @param maxDelayMs the cap for the backoff delay in milliseconds
   */
  public record Backoff(
      @DefaultValue("30000") long baseDelayMs, @DefaultValue("3600000") long maxDelayMs) {}
}
