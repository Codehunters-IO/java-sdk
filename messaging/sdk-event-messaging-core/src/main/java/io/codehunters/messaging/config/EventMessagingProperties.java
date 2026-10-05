package io.codehunters.messaging.config;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
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

  private static final String PREFIX = "codehunters.outbox.";
  private static final String KAFKA = "kafka";
  private static final Set<String> PUBLISHER_TYPES = Set.of("rabbit", KAFKA);
  private static final String CLAIM_BUDGET_MESSAGE =
      PREFIX
          + "claim.ttl-ms (%d) must exceed "
          + PREFIX
          + "batch-size * "
          + PREFIX
          + "publisher.kafka.send-timeout (%d ms): a batch still being sent when its claim"
          + " expires is reclaimed by another instance and published as duplicates";

  /**
   * Validates the ranges and the claim budget of the Kafka publisher.
   *
   * @throws IllegalArgumentException when a value is out of range, naming the offending key; with
   *     the Kafka publisher, also when {@code claim.ttl-ms} does not exceed {@code batch-size}
   *     times {@code publisher.kafka.send-timeout}
   */
  public EventMessagingProperties {
    requireAtLeastOne(batchSize, "batch-size");
    requireAtLeastOne(maxAttempts, "max-attempts");
    requireClaimBudget(batchSize, claim, publisher);
  }

  // A claimed batch must be published before its claim goes stale; otherwise another instance
  // reclaims rows that are still being sent and the broker receives them twice.
  private static void requireClaimBudget(int batchSize, Claim claim, Publisher publisher) {
    if (claim == null || publisher == null || !KAFKA.equals(publisher.type())) {
      return;
    }
    long budgetMs = batchSize * publisher.kafka().sendTimeout().toMillis();
    if (claim.ttlMs() <= budgetMs) {
      throw new IllegalArgumentException(CLAIM_BUDGET_MESSAGE.formatted(claim.ttlMs(), budgetMs));
    }
  }

  private static void requireAtLeastOne(long value, String key) {
    if (value < 1) {
      throw new IllegalArgumentException(PREFIX + key + " must be at least 1 but was " + value);
    }
  }

  private static void requirePositive(Duration value, String key) {
    if (value == null || value.isNegative() || value.isZero()) {
      throw new IllegalArgumentException(PREFIX + key + " must be a positive duration");
    }
  }

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
  public record Claim(@DefaultValue("300000") long ttlMs, @DefaultValue("auto") String dialect) {

    /** Validates the claim TTL. */
    public Claim {
      requireAtLeastOne(ttlMs, "claim.ttl-ms");
    }
  }

  /**
   * Retry backoff configuration.
   *
   * @param baseDelayMs the initial backoff delay in milliseconds (doubled on each retry)
   * @param maxDelayMs the cap for the backoff delay in milliseconds
   */
  public record Backoff(
      @DefaultValue("30000") long baseDelayMs, @DefaultValue("3600000") long maxDelayMs) {

    /** Validates the delays. */
    public Backoff {
      requireAtLeastOne(baseDelayMs, "backoff.base-delay-ms");
      requireAtLeastOne(maxDelayMs, "backoff.max-delay-ms");
    }
  }

  /**
   * Broker publisher selection.
   *
   * @param type the publisher type: {@code rabbit} (default) or {@code kafka}, case-insensitive and
   *     normalised to lower case; other values fail startup
   * @param kafka the Kafka publisher settings, used when {@code type} is {@code kafka}
   */
  public record Publisher(@DefaultValue("rabbit") String type, @DefaultValue Kafka kafka) {

    /** Validates and normalises the publisher type. */
    public Publisher {
      String normalised = type == null ? null : type.trim().toLowerCase(Locale.ROOT);
      if (normalised == null || !PUBLISHER_TYPES.contains(normalised)) {
        throw new IllegalArgumentException(
            PREFIX + "publisher.type must be 'rabbit' or 'kafka' but was '" + type + "'");
      }
      type = normalised;
    }
  }

  /**
   * Kafka publisher settings.
   *
   * @param sendTimeout the maximum time to wait for a broker acknowledgement per message; with
   *     {@code batch-size}, it bounds the claim TTL
   */
  public record Kafka(@DefaultValue("5s") Duration sendTimeout) {

    /** Validates the send timeout. */
    public Kafka {
      requirePositive(sendTimeout, "publisher.kafka.send-timeout");
    }
  }

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
      @DefaultValue("PT30S") String lockAtLeast) {

    /** Validates the retention and batch size. */
    public Purge {
      requirePositive(retention, "purge.retention");
      requireAtLeastOne(batchSize, "purge.batch-size");
    }
  }
}
