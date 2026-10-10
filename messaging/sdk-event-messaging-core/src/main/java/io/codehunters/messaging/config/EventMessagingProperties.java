package io.codehunters.messaging.config;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the event-messaging SDK, bound from the {@code codehunters.outbox} prefix.
 *
 * @param enabled whether the outbox SDK is active
 * @param batchSize the number of messages dispatched per scheduler tick
 * @param maxAttempts the maximum number of publish attempts before marking a message FAILED
 * @param task the scheduler/ShedLock configuration
 * @param claim the claim-lifetime configuration
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
  private static final String CLAIM_TTL_KEY = "claim.ttl";
  private static final String LOCK_KEY = "task.lock-at-most";
  private static final String CLAIM_BUDGET_MESSAGE =
      PREFIX
          + CLAIM_TTL_KEY
          + " (%s) must exceed "
          + PREFIX
          + "batch-size * "
          + PREFIX
          + "%s (%d ms): a batch still being sent when its claim expires is reclaimed by another"
          + " instance and published as duplicates";
  private static final String DISPATCH_LOCK_MESSAGE =
      PREFIX
          + LOCK_KEY
          + " (%s) must exceed half of "
          + PREFIX
          + CLAIM_TTL_KEY
          + " plus "
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
   * claim.ttl} must exceed {@code batch-size} times that timeout. A dispatch run stops starting
   * sends once half of {@code claim.ttl} has passed, so it lasts at most that half plus one
   * timeout; while {@code scheduling.enabled} is {@code true}, {@code task.lock-at-most} must
   * exceed it. Transport auto-configurations call it once the transport is resolved, which covers
   * an unset {@code publisher.type}.
   *
   * @param transport the resolved transport, {@code rabbit} or {@code kafka}
   * @throws IllegalArgumentException when {@code claim.ttl} does not exceed the batch budget, or
   *     {@code task.lock-at-most} does not exceed a dispatch run
   */
  public void requireClaimBudget(String transport) {
    TransportTimeout timeout = TransportTimeout.of(publisher, transport);
    checkClaimBudget(batchSize, claim, timeout);
    checkDispatchLock(task, scheduling, claim, timeout);
  }

  /**
   * Returns the longest a dispatch run keeps publishing: half the claim TTL, after which the rest
   * of the batch is released, plus one message's transport timeout. With {@code publisher.type}
   * unset, the longer of the Kafka and RabbitMQ timeouts is used.
   *
   * @return the bound of one dispatch run
   */
  public Duration maxDispatchRun() {
    return claim.ttl().dividedBy(2).plus(transportTimeout());
  }

  private Duration transportTimeout() {
    if (publisher.type() != null) {
      return TransportTimeout.of(publisher, publisher.type()).value();
    }
    Duration kafka = publisher.kafka().sendTimeout();
    Duration rabbit = publisher.rabbit().confirmTimeout();
    return kafka.compareTo(rabbit) >= 0 ? kafka : rabbit;
  }

  // A claimed batch must be published before its claim goes stale; otherwise another instance
  // reclaims rows that are still being sent and the broker receives them twice.
  private static void checkClaimBudget(int batchSize, Claim claim, TransportTimeout timeout) {
    long budgetMs = batchBudgetMs(batchSize, timeout);
    if (claim.ttl().toMillis() <= budgetMs) {
      throw new IllegalArgumentException(
          CLAIM_BUDGET_MESSAGE.formatted(claim.ttl(), timeout.key(), budgetMs));
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
    long runMs = saturatedAdd(claim.ttl().toMillis() / 2, timeout.value().toMillis());
    if (task.lockAtMost().toMillis() <= runMs) {
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

  private static void requireNotNegative(Duration value, String key) {
    if (value == null || value.isNegative()) {
      throw new IllegalArgumentException(PREFIX + key + " must be zero or a positive duration");
    }
  }

  // ShedLock refuses a lock configuration whose minimum hold exceeds its maximum.
  private static void requireLockWindow(LockWindow window, String prefix) {
    requirePositive(window.atMost(), prefix + "lock-at-most");
    requireNotNegative(window.atLeast(), prefix + "lock-at-least");
    if (window.atLeast().compareTo(window.atMost()) > 0) {
      throw new IllegalArgumentException(
          PREFIX + prefix + "lock-at-least must not exceed " + PREFIX + prefix + "lock-at-most");
    }
  }

  /**
   * Dispatch scheduler and ShedLock configuration. Plain numbers are read as milliseconds; units
   * ({@code 5s}, {@code 5m}) and ISO-8601 ({@code PT5M}) are accepted.
   *
   * @param fixedDelay the delay between the end of one dispatch poll and the start of the next
   * @param lockAtMost the maximum time a node may hold the dispatch lock; while scheduling is on,
   *     it must exceed half of {@code claim.ttl} plus the transport timeout
   * @param lockAtLeast the minimum time a node holds the dispatch lock after a run; {@code 0} (the
   *     default) lets the next poll, on any node, start as soon as the previous run ends
   * @param lockName the ShedLock lock name of the dispatch task; unset (the default) uses {@code
   *     outbox-} followed by {@code spring.application.name}
   */
  public record Task(
      @DefaultValue("5s") Duration fixedDelay,
      @DefaultValue("5m") Duration lockAtMost,
      @DefaultValue("0s") Duration lockAtLeast,
      String lockName) {

    /** Validates the delay and the lock window. */
    public Task {
      requirePositive(fixedDelay, "task.fixed-delay");
      requireLockWindow(new LockWindow(lockAtMost, lockAtLeast), "task.");
    }
  }

  /**
   * Claim configuration.
   *
   * @param ttl the time after which an in-flight claim is considered stale and reclaimable
   * @param maxPerKey the maximum number of rows of one {@code (topic, key)} a claim takes together,
   *     in insertion order; they are published one after the other and, after the first one that is
   *     not published, the rest are released. {@code 1} restores one row per key per claim
   * @param commitGrace how long a row with a key is held back after its {@code occurred_at}, so an
   *     earlier row of the same key whose transaction commits later still goes first; {@code 0}
   *     (the default) holds nothing back. It must cover the time from {@code occurred_at} to commit
   *     of the slowest writer of a key, and delays every keyed row by at least that long
   */
  public record Claim(
      @DefaultValue("5m") Duration ttl,
      @DefaultValue("10") int maxPerKey,
      @DefaultValue("0s") Duration commitGrace) {

    /** Validates the claim TTL, the per-key batch and the commit grace. */
    public Claim {
      requirePositive(ttl, CLAIM_TTL_KEY);
      requireAtLeastOne(maxPerKey, "claim.max-per-key");
      requireNotNegative(commitGrace, "claim.commit-grace");
    }
  }

  /**
   * Retry backoff configuration.
   *
   * @param baseDelay the initial backoff delay (doubled on each retry)
   * @param maxDelay the cap for the backoff delay
   */
  public record Backoff(
      @DefaultValue("30s") Duration baseDelay, @DefaultValue("1h") Duration maxDelay) {

    /** Validates the delays. */
    public Backoff {
      requirePositive(baseDelay, "backoff.base-delay");
      requirePositive(maxDelay, "backoff.max-delay");
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
   * @param legacyTraceparentHeader {@code true} also sends the stored trace context as the {@code
   *     x-traceparent} header, for consumers that still read only that one; {@code false} (the
   *     default) sends {@code traceparent} only
   */
  public record Publisher(
      String type,
      @DefaultValue Kafka kafka,
      @DefaultValue Rabbit rabbit,
      @DefaultValue("false") boolean legacyTraceparentHeader) {

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
   * @param fixedDelay the delay between the end of one purge run and the start of the next
   * @param initialDelay the delay before the first purge run
   * @param lockName the ShedLock lock name of the purge job; unset (the default) uses {@code
   *     outbox-purge-} followed by {@code spring.application.name}
   * @param lockAtMost the maximum time a node may hold the purge lock
   * @param lockAtLeast the minimum time a node holds the purge lock
   */
  public record Purge(
      @DefaultValue("7d") Duration retention,
      @DefaultValue("500") int batchSize,
      @DefaultValue("1h") Duration fixedDelay,
      @DefaultValue("1m") Duration initialDelay,
      String lockName,
      @DefaultValue("10m") Duration lockAtMost,
      @DefaultValue("30s") Duration lockAtLeast) {

    /** Validates the retention, batch size, delays and lock window. */
    public Purge {
      requirePositive(retention, "purge.retention");
      requireAtLeastOne(batchSize, "purge.batch-size");
      requirePositive(fixedDelay, "purge.fixed-delay");
      requireNotNegative(initialDelay, "purge.initial-delay");
      requireLockWindow(new LockWindow(lockAtMost, lockAtLeast), "purge.");
    }
  }

  /**
   * Scheduling switches of {@code sdk-event-messaging-jdbc}.
   *
   * @param enabled {@code true} (the default) runs the ShedLock-protected dispatch and purge tasks
   *     on the SDK's own scheduler; {@code false} leaves dispatch and purge to the application, for
   *     example when another process runs them
   * @param dispatchOnCommit {@code true} (the default) also dispatches right after a transaction
   *     that saved outbox messages commits, without waiting for the next poll; only while {@code
   *     enabled} is {@code true}
   */
  public record Scheduling(
      @DefaultValue("true") boolean enabled, @DefaultValue("true") boolean dispatchOnCommit) {}

  // The hold bounds of one ShedLock lock.
  private record LockWindow(Duration atMost, Duration atLeast) {}

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
