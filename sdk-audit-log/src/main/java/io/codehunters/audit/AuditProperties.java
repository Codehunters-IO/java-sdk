package io.codehunters.audit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the audit SDK, bound from the {@code codehunters.audit} prefix. The 2.x {@code
 * vitxo.audit.*} keys are honoured until 4.0.0.
 *
 * @param failOnError whether audit failures propagate to the audited call; when {@code true} the
 *     JDBC adapter publishes synchronously, on the caller's thread, and the event of a successful
 *     call commits or rolls back with the caller's transaction
 * @param order the precedence of the audit aspect among other advice; the default, the lowest
 *     precedence, runs it inside the transaction advice of {@code @Transactional} methods
 * @param async the executor that publishes audit events off the caller's thread
 */
@ConfigurationProperties(prefix = "codehunters.audit")
public record AuditProperties(
    @DefaultValue("false") boolean failOnError,
    @DefaultValue("2147483647") int order,
    @DefaultValue Async async) {

  /**
   * Settings of the {@code auditTaskExecutor}.
   *
   * @param enabled whether the JDBC adapter publishes asynchronously; ignored when {@code
   *     fail-on-error} is {@code true}
   * @param corePoolSize threads kept alive; at least 1
   * @param maxPoolSize thread ceiling once the queue is full; at least {@code core-pool-size}
   * @param queueCapacity events waiting for a thread; when full, events are rejected, logged and
   *     counted, never silently lost
   * @param awaitTermination how long shutdown waits for queued events to be written
   */
  public record Async(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("2") int corePoolSize,
      @DefaultValue("4") int maxPoolSize,
      @DefaultValue("500") int queueCapacity,
      @DefaultValue("10s") Duration awaitTermination) {

    /**
     * Validates the executor settings.
     *
     * @throws IllegalArgumentException if a size is out of range or the wait is negative
     */
    public Async {
      require(corePoolSize >= 1, "core-pool-size must be at least 1");
      require(maxPoolSize >= corePoolSize, "max-pool-size must be at least core-pool-size");
      require(queueCapacity >= 0, "queue-capacity must not be negative");
      require(!awaitTermination.isNegative(), "await-termination must not be negative");
    }

    private static void require(boolean condition, String message) {
      if (!condition) {
        throw new IllegalArgumentException("codehunters.audit.async." + message);
      }
    }
  }
}
