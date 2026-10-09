package io.codehunters.messaging.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationStyle;

/**
 * Configuration for the event-messaging SDK, bound from the {@code codehunters.outbox} prefix.
 *
 * @param enabled whether the outbox SDK is active
 * @param batchSize the number of messages dispatched per scheduler tick
 * @param maxAttempts the maximum number of publish attempts before marking a message FAILED
 * @param task the scheduler/ShedLock configuration
 * @param claim the claim-lifetime and claim-dialect configuration
 * @param backoff the retry backoff configuration
 * @param publisher the broker publisher selection
 * @param purge the PUBLISHED-row retention configuration
 * @param scheduling whether the SDK schedules dispatch and purge itself
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
    @DefaultValue Purge purge,
    @DefaultValue Scheduling scheduling) {

  private static final String PREFIX = "codehunters.outbox.";
  private static final String RABBIT = "rabbit";
  private static final String KAFKA = "kafka";
  private static final Set<String> PUBLISHER_TYPES = Set.of(RABBIT, KAFKA);
  private static final String CLAIM_BUDGET_MESSAGE =
      PREFIX
          + "claim.ttl-ms (%d) must exceed "
          + PREFIX
          + "batch-size * "
          + PREFIX
          + "%s (%d ms): a batch still being sent when its claim expires is reclaimed by another"
          + " instance and published as duplicates";
  private static final String LOCK_KEY = "task.lock-at-most";
  private static final String DISPATCH_LOCK_MESSAGE =
      PREFIX
          + LOCK_KEY
          + " (%s) must exceed half of "
          + PREFIX
          + "claim.ttl-ms plus "
          + PREFIX
          + "%s (%d ms): ShedLock would release the dispatch lock while a run is still publishing"
          + " and let another instance start a concurrent run; raise it, or set "
          + PREFIX
          + "scheduling.enabled=false when dispatch is triggered elsewhere";

  /**
   * Validates the ranges and, when {@code publisher.type} is set, the claim budget and dispatch
   * lock of that transport (see {@link #requireClaimBudget(String)}).
   *
   * @throws IllegalArgumentException when a value is out of range, naming the offending key, or
   *     when the claim budget or dispatch lock of the configured transport is exceeded
   */
  public EventMessagingProperties {
    requireAtLeastOne(batchSize, "batch-size");
    requireAtLeastOne(maxAttempts, "max-attempts");
    if (claim != null && publisher != null && publisher.type() != null) {
      TransportTimeout timeout = TransportTimeout.of(publisher, publisher.type());
      checkClaimBudget(batchSize, claim, timeout);
      checkDispatchLock(task, scheduling, claim, timeout);
    }
  }

  /**
   * Validates the dispatch timing of the transport that publishes the outbox. A claimed batch is
   * published sequentially, each send blocking for at most the per-message timeout of the transport
   * ({@code publisher.kafka.send-timeout} or {@code publisher.rabbit.confirm-timeout}); {@code
   * claim.ttl-ms} must exceed {@code batch-size} times that timeout. A dispatch run stops starting
   * sends once half of {@code claim.ttl-ms} has passed, so it lasts at most that half plus one
   * timeout; while {@code scheduling.enabled} is {@code true}, {@code task.lock-at-most} must
   * exceed it. Transport auto-configurations call it once the transport is resolved, which covers
   * an unset {@code publisher.type}.
   *
   * @param transport the resolved transport, {@code rabbit} or {@code kafka}
   * @throws IllegalArgumentException when {@code claim.ttl-ms} does not exceed the batch budget, or
   *     {@code task.lock-at-most} is not a duration or does not exceed a dispatch run
   */
  public void requireClaimBudget(String transport) {
    TransportTimeout timeout = TransportTimeout.of(publisher, transport);
    checkClaimBudget(batchSize, claim, timeout);
    checkDispatchLock(task, scheduling, claim, timeout);
  }

  // A claimed batch must be published before its claim goes stale; otherwise another instance
  // reclaims rows that are still being sent and the broker receives them twice.
  private static void checkClaimBudget(int batchSize, Claim claim, TransportTimeout timeout) {
    long budgetMs = batchBudgetMs(batchSize, timeout);
    if (claim.ttlMs() <= budgetMs) {
      throw new IllegalArgumentException(
          CLAIM_BUDGET_MESSAGE.formatted(claim.ttlMs(), timeout.key(), budgetMs));
    }
  }

  // Exact arithmetic: a wrapped product would turn a huge timeout into a small or negative budget
  // and let the claim TTL check pass.
  private static long batchBudgetMs(int batchSize, TransportTimeout timeout) {
    try {
      return Math.multiplyExact(batchSize, timeout.value().toMillis());
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException(
          PREFIX + timeout.key() + " is too large: batch-size times it overflows milliseconds");
    }
  }

  // ShedLock frees the lock after lock-at-most even if the run is still going; a shorter lock lets
  // a second instance start dispatching next to the first.
  private static void checkDispatchLock(
      Task task, Scheduling scheduling, Claim claim, TransportTimeout timeout) {
    if (task == null || scheduling == null || !scheduling.enabled()) {
      return;
    }
    long runMs = saturatedAdd(claim.ttlMs() / 2, timeout.value().toMillis());
    if (lockAtMostMs(task.lockAtMost()) <= runMs) {
      throw new IllegalArgumentException(
          DISPATCH_LOCK_MESSAGE.formatted(task.lockAtMost(), timeout.key(), runMs));
    }
  }

  private static long saturatedAdd(long left, long right) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException e) {
      return Long.MAX_VALUE;
    }
  }

  // Same formats ShedLock accepts: plain milliseconds, ISO-8601 (PT5M) or simple (5m).
  private static long lockAtMostMs(String lockAtMost) {
    try {
      return DurationStyle.detectAndParse(lockAtMost, ChronoUnit.MILLIS).toMillis();
    } catch (IllegalArgumentException | ArithmeticException e) {
      throw new IllegalArgumentException(
          PREFIX
              + LOCK_KEY
              + " must be a duration (ms, ISO-8601 or 5m) but was '"
              + lockAtMost
              + "'");
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
   * @param lockAtMost the maximum time a node may hold the dispatch lock; while scheduling is on,
   *     it must exceed half of {@code claim.ttl-ms} plus the transport timeout
   * @param lockAtLeast the minimum time a node holds the dispatch lock before releasing it
   * @param lockName the ShedLock lock name; defaults to the current application name
   */
  public record Task(
      @DefaultValue("5000") String fixedDelay,
      @DefaultValue("300000") String lockAtMost,
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
   * @param type the publisher type: {@code rabbit} or {@code kafka}, case-insensitive and
   *     normalised to lower case; other values fail startup. Unset ({@code null}) lets the
   *     transport artifact on the classpath decide; with both artifacts present, startup fails
   * @param kafka the Kafka publisher settings, used when the transport is Kafka
   * @param rabbit the RabbitMQ publisher settings, used when the transport is RabbitMQ
   */
  public record Publisher(String type, @DefaultValue Kafka kafka, @DefaultValue Rabbit rabbit) {

    /** Validates and normalises the publisher type. */
    public Publisher {
      String normalised =
          type == null || type.isBlank() ? null : type.trim().toLowerCase(Locale.ROOT);
      if (normalised != null && !PUBLISHER_TYPES.contains(normalised)) {
        throw new IllegalArgumentException(
            PREFIX + "publisher.type must be 'rabbit' or 'kafka' but was '" + type + "'");
      }
      type = normalised;
    }
  }

  /**
   * RabbitMQ publisher settings.
   *
   * @param confirmTimeout the maximum time to wait for the broker confirm of one message; with
   *     {@code batch-size}, it bounds the claim TTL
   */
  public record Rabbit(@DefaultValue("5s") Duration confirmTimeout) {

    /** Validates the confirm timeout. */
    public Rabbit {
      requirePositive(confirmTimeout, "publisher.rabbit.confirm-timeout");
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

  /**
   * Scheduling switch of {@code sdk-event-messaging-jdbc}.
   *
   * @param enabled {@code true} (the default) registers the ShedLock-protected dispatch and purge
   *     schedulers and enables Spring scheduling; {@code false} leaves dispatch and purge to the
   *     application, for example when another process runs them
   */
  public record Scheduling(@DefaultValue("true") boolean enabled) {}

  // The per-message timeout of the resolved transport and the key that configures it.
  private record TransportTimeout(Duration value, String key) {

    static TransportTimeout of(Publisher publisher, String transport) {
      return KAFKA.equals(transport)
          ? new TransportTimeout(publisher.kafka().sendTimeout(), "publisher.kafka.send-timeout")
          : new TransportTimeout(
              publisher.rabbit().confirmTimeout(), "publisher.rabbit.confirm-timeout");
    }
  }
}
