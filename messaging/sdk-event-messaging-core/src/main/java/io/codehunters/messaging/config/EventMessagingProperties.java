package io.codehunters.messaging.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the event-messaging SDK, bound from the {@code codehunters.outbox} prefix. The
 * pre-6.0 {@code outbox.*} keys are still honoured through {@link OutboxLegacyProperties}.
 *
 * @param enabled whether the outbox SDK is active
 * @param batchSize the number of messages dispatched per scheduler tick
 * @param maxAttempts the maximum number of publish attempts before marking a message FAILED
 * @param task the scheduler/ShedLock configuration
 * @param claim the claim-lifetime and claim-dialect configuration
 * @param backoff the retry backoff configuration
 * @param publisher the broker publisher selection
 * @param purge the PUBLISHED-row retention configuration
 */
@ConfigurationProperties(prefix = "codehunters.outbox")
public record EventMessagingProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("50") int batchSize,
    @DefaultValue("5") int maxAttempts,
    @DefaultValue Task task,
    @DefaultValue Claim claim,
    @DefaultValue Backoff backoff,
    @DefaultValue Publisher publisher,
    @DefaultValue Purge purge) {

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
   * Claim configuration.
   *
   * @param ttlMs the time after which an in-flight claim is considered stale and reclaimable
   * @param dialect the claim SQL dialect: {@code auto} (detected from the database), {@code
   *     postgresql}, {@code sqlserver} or {@code generic}; unknown values fail startup
   */
  public record Claim(@DefaultValue("300000") long ttlMs, @DefaultValue("auto") String dialect) {}

  /**
   * Retry backoff configuration.
   *
   * @param baseDelayMs the initial backoff delay in milliseconds (doubled on each retry)
   * @param maxDelayMs the cap for the backoff delay in milliseconds
   */
  public record Backoff(
      @DefaultValue("30000") long baseDelayMs, @DefaultValue("3600000") long maxDelayMs) {}

  /**
   * Broker publisher selection.
   *
   * @param type the publisher type: {@code rabbit} (default) or {@code kafka}
   * @param kafka the Kafka publisher settings, used when {@code type} is {@code kafka}
   */
  public record Publisher(@DefaultValue("rabbit") String type, @DefaultValue Kafka kafka) {}

  /**
   * Kafka publisher settings.
   *
   * @param sendTimeout the maximum time to wait for a broker acknowledgement per message
   */
  public record Kafka(@DefaultValue("10s") Duration sendTimeout) {}

  /**
   * Retention of PUBLISHED outbox rows.
   *
   * @param retention how long PUBLISHED rows are kept before the purge job deletes them
   * @param batchSize the maximum number of rows deleted per purge statement
   * @param fixedDelay the fixed delay between purge runs, in milliseconds
   * @param initialDelay the delay before the first purge run, in milliseconds
   * @param lockName the ShedLock lock name of the purge job; defaults to the application name
   * @param lockAtMost the maximum time a node may hold the purge lock (ISO-8601 duration)
   * @param lockAtLeast the minimum time a node holds the purge lock (ISO-8601 duration)
   */
  public record Purge(
      @DefaultValue("7d") Duration retention,
      @DefaultValue("500") int batchSize,
      @DefaultValue("3600000") String fixedDelay,
      @DefaultValue("60000") String initialDelay,
      @DefaultValue("outbox-purge-${spring.application.name:unknown}") String lockName,
      @DefaultValue("PT10M") String lockAtMost,
      @DefaultValue("PT30S") String lockAtLeast) {}
}
