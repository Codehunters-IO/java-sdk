package io.codehunters.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.codehunters.messaging.config.EventMessagingProperties.Backoff;
import io.codehunters.messaging.config.EventMessagingProperties.Claim;
import io.codehunters.messaging.config.EventMessagingProperties.Kafka;
import io.codehunters.messaging.config.EventMessagingProperties.Publisher;
import io.codehunters.messaging.config.EventMessagingProperties.Purge;
import io.codehunters.messaging.config.EventMessagingProperties.Rabbit;
import io.codehunters.messaging.config.EventMessagingProperties.Scheduling;
import io.codehunters.messaging.config.EventMessagingProperties.Task;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class EventMessagingPropertiesTest {

  private static final int BATCH_SIZE = 50;
  private static final int MAX_ATTEMPTS = 5;
  private static final Duration CLAIM_TTL = Duration.ofMinutes(5);
  private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration LONG_CONFIRM_TIMEOUT = Duration.ofSeconds(9);
  private static final Duration TTL_BELOW_BUDGET = Duration.ofSeconds(250);
  private static final Duration SHORT_TTL = Duration.ofSeconds(1);
  private static final Task TASK = task(Duration.ofMinutes(5), Duration.ZERO);
  private static final Scheduling SCHEDULING = new Scheduling(true, true);
  private static final String LOCK_KEY = "codehunters.outbox.task.lock-at-most";
  private static final Backoff BACKOFF = new Backoff(Duration.ofSeconds(30), Duration.ofHours(1));
  private static final Purge PURGE = purge(Duration.ofDays(7), 500);

  @Test
  void should_reject_batch_size_when_below_one() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties(0, MAX_ATTEMPTS, claim(CLAIM_TTL), rabbit()))
        .withMessageContaining("codehunters.outbox.batch-size");
  }

  @Test
  void should_reject_max_attempts_when_below_one() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties(BATCH_SIZE, 0, claim(CLAIM_TTL), rabbit()))
        .withMessageContaining("codehunters.outbox.max-attempts");
  }

  @Test
  void should_reject_claim_ttl_when_not_positive() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> claim(Duration.ZERO))
        .withMessageContaining("codehunters.outbox.claim.ttl");
  }

  @Test
  void should_reject_max_per_key_when_below_one() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Claim(CLAIM_TTL, 0, Duration.ZERO))
        .withMessageContaining("codehunters.outbox.claim.max-per-key");
  }

  @Test
  void should_reject_base_backoff_when_not_positive() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Backoff(Duration.ZERO, Duration.ofSeconds(1)))
        .withMessageContaining("codehunters.outbox.backoff.base-delay");
  }

  @Test
  void should_reject_max_backoff_when_not_positive() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Backoff(Duration.ofSeconds(1), Duration.ofMillis(-1)))
        .withMessageContaining("codehunters.outbox.backoff.max-delay");
  }

  @Test
  void should_reject_kafka_send_timeout_when_not_positive() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Kafka(Duration.ZERO))
        .withMessageContaining("codehunters.outbox.publisher.kafka.send-timeout");
  }

  @Test
  void should_reject_kafka_send_timeout_when_missing() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Kafka(null))
        .withMessageContaining("codehunters.outbox.publisher.kafka.send-timeout");
  }

  @Test
  void should_reject_purge_retention_when_not_positive() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> purge(Duration.ofDays(-1), 500))
        .withMessageContaining("codehunters.outbox.purge.retention");
  }

  @Test
  void should_reject_purge_retention_when_missing() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> purge(null, 500))
        .withMessageContaining("codehunters.outbox.purge.retention");
  }

  @Test
  void should_reject_purge_batch_size_when_below_one() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> purge(Duration.ofDays(7), 0))
        .withMessageContaining("codehunters.outbox.purge.batch-size");
  }

  @Test
  void should_reject_publisher_type_when_unknown() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> publisher("sqs"))
        .withMessageContaining("codehunters.outbox.publisher.type")
        .withMessageContaining("sqs");
  }

  @Test
  void should_leave_publisher_type_unset_when_missing() {
    assertThat(publisher(null).type()).isNull();
  }

  @Test
  void should_leave_publisher_type_unset_when_blank() {
    assertThat(publisher("  ").type()).isNull();
  }

  @Test
  void should_accept_publisher_type_ignoring_case_when_known() {
    Publisher publisher = publisher("KaFkA");

    assertThat(publisher.type()).isEqualTo("kafka");
  }

  @Test
  void should_reject_claim_ttl_explaining_duplicates_when_kafka_batch_budget_exceeds_it() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties(BATCH_SIZE, MAX_ATTEMPTS, claim(TTL_BELOW_BUDGET), kafka()))
        .withMessageContaining("codehunters.outbox.claim.ttl ")
        .withMessageContaining("duplicate");
  }

  @Test
  void should_accept_kafka_publisher_when_claim_ttl_exceeds_batch_budget() {
    EventMessagingProperties properties =
        properties(BATCH_SIZE, MAX_ATTEMPTS, claim(CLAIM_TTL), kafka());

    assertThat(properties.publisher().type()).isEqualTo("kafka");
  }

  @Test
  void should_reject_rabbit_confirm_timeout_when_not_positive() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Rabbit(Duration.ofSeconds(-1)))
        .withMessageContaining("codehunters.outbox.publisher.rabbit.confirm-timeout");
  }

  @Test
  void should_reject_rabbit_confirm_timeout_when_missing() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Rabbit(null))
        .withMessageContaining("codehunters.outbox.publisher.rabbit.confirm-timeout");
  }

  @Test
  void should_reject_claim_ttl_naming_confirm_timeout_when_rabbit_batch_budget_exceeds_it() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties(BATCH_SIZE, MAX_ATTEMPTS, claim(TTL_BELOW_BUDGET), rabbit()))
        .withMessageContaining("codehunters.outbox.claim.ttl ")
        .withMessageContaining("codehunters.outbox.publisher.rabbit.confirm-timeout")
        .withMessageContaining("duplicate");
  }

  @Test
  void should_accept_rabbit_publisher_when_claim_ttl_exceeds_batch_budget() {
    EventMessagingProperties properties =
        properties(BATCH_SIZE, MAX_ATTEMPTS, claim(CLAIM_TTL), rabbit());

    assertThat(properties.publisher().type()).isEqualTo("rabbit");
  }

  @Test
  void should_ignore_kafka_send_timeout_when_publisher_is_rabbit() {
    Publisher publisher =
        new Publisher("rabbit", new Kafka(Duration.ofHours(1)), new Rabbit(CONFIRM_TIMEOUT), false);

    EventMessagingProperties properties =
        properties(BATCH_SIZE, MAX_ATTEMPTS, claim(CLAIM_TTL), publisher);

    assertThat(properties.claim().ttl()).isEqualTo(CLAIM_TTL);
  }

  @Test
  void should_defer_claim_budget_when_publisher_type_unset() {
    EventMessagingProperties properties =
        properties(BATCH_SIZE, MAX_ATTEMPTS, claim(SHORT_TTL), publisher(null));

    assertThat(properties.publisher().type()).isNull();
  }

  @Test
  void should_reject_claim_ttl_when_budget_of_resolved_rabbit_transport_exceeds_it() {
    EventMessagingProperties properties =
        properties(BATCH_SIZE, MAX_ATTEMPTS, claim(SHORT_TTL), publisher(null));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties.requireClaimBudget("rabbit"))
        .withMessageContaining("codehunters.outbox.publisher.rabbit.confirm-timeout");
  }

  @Test
  void should_reject_claim_ttl_when_budget_of_resolved_kafka_transport_exceeds_it() {
    EventMessagingProperties properties =
        properties(BATCH_SIZE, MAX_ATTEMPTS, claim(SHORT_TTL), publisher(null));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties.requireClaimBudget("kafka"))
        .withMessageContaining("codehunters.outbox.publisher.kafka.send-timeout");
  }

  @Test
  void should_reject_send_timeout_when_batch_budget_overflows_long() {
    Publisher publisher =
        new Publisher(
            "kafka",
            new Kafka(Duration.ofMillis(Long.MAX_VALUE / 25)),
            new Rabbit(CONFIRM_TIMEOUT),
            false);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties(BATCH_SIZE, MAX_ATTEMPTS, claim(CLAIM_TTL), publisher))
        .withMessageContaining("codehunters.outbox.publisher.kafka.send-timeout");
  }

  @Test
  void should_reject_confirm_timeout_when_it_overflows_milliseconds() {
    Publisher publisher =
        new Publisher(
            "rabbit",
            new Kafka(SEND_TIMEOUT),
            new Rabbit(Duration.ofSeconds(Long.MAX_VALUE)),
            false);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties(BATCH_SIZE, MAX_ATTEMPTS, claim(CLAIM_TTL), publisher))
        .withMessageContaining("codehunters.outbox.publisher.rabbit.confirm-timeout");
  }

  @Test
  void should_accept_resolved_transport_when_claim_ttl_exceeds_its_budget() {
    EventMessagingProperties properties =
        properties(BATCH_SIZE, MAX_ATTEMPTS, claim(CLAIM_TTL), publisher(null));

    properties.requireClaimBudget("kafka");

    assertThat(properties.claim().ttl()).isEqualTo(CLAIM_TTL);
  }

  @Test
  void should_reject_lock_at_most_naming_keys_when_shorter_than_dispatch_run() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> scheduled(lockAtMost(Duration.ofSeconds(60)), SCHEDULING, kafka()))
        .withMessageContaining(LOCK_KEY)
        .withMessageContaining("codehunters.outbox.claim.ttl ")
        .withMessageContaining("codehunters.outbox.publisher.kafka.send-timeout");
  }

  @Test
  void should_accept_lock_at_most_when_it_exceeds_dispatch_run() {
    EventMessagingProperties properties =
        scheduled(lockAtMost(Duration.ofMinutes(3)), SCHEDULING, rabbit());

    assertThat(properties.task().lockAtMost()).isEqualTo(Duration.ofMinutes(3));
  }

  @Test
  void should_skip_lock_check_when_scheduling_disabled() {
    EventMessagingProperties properties =
        scheduled(lockAtMost(Duration.ofSeconds(60)), new Scheduling(false, true), kafka());

    assertThat(properties.scheduling().enabled()).isFalse();
  }

  @Test
  void should_reject_task_fixed_delay_when_not_positive() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Task(Duration.ZERO, Duration.ofMinutes(5), Duration.ZERO, "lock"))
        .withMessageContaining("codehunters.outbox.task.fixed-delay");
  }

  @Test
  void should_reject_task_lock_at_most_when_not_positive() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> task(Duration.ZERO, Duration.ZERO))
        .withMessageContaining(LOCK_KEY);
  }

  @Test
  void should_reject_task_lock_at_least_when_negative() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> task(Duration.ofMinutes(5), Duration.ofSeconds(-1)))
        .withMessageContaining("codehunters.outbox.task.lock-at-least");
  }

  @Test
  void should_reject_task_lock_at_least_when_longer_than_lock_at_most() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> task(Duration.ofSeconds(10), Duration.ofSeconds(11)))
        .withMessageContaining("codehunters.outbox.task.lock-at-least")
        .withMessageContaining(LOCK_KEY);
  }

  @Test
  void should_accept_zero_task_lock_at_least_when_configured() {
    assertThat(task(Duration.ofMinutes(5), Duration.ZERO).lockAtLeast()).isZero();
  }

  @Test
  void should_reject_purge_fixed_delay_when_not_positive() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> purgeTiming(Duration.ZERO, Duration.ZERO, Duration.ZERO))
        .withMessageContaining("codehunters.outbox.purge.fixed-delay");
  }

  @Test
  void should_reject_purge_initial_delay_when_negative() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> purgeTiming(Duration.ofHours(1), Duration.ofMillis(-1), Duration.ZERO))
        .withMessageContaining("codehunters.outbox.purge.initial-delay");
  }

  @Test
  void should_reject_purge_lock_at_least_when_longer_than_lock_at_most() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> purgeTiming(Duration.ofHours(1), Duration.ZERO, Duration.ofMinutes(11)))
        .withMessageContaining("codehunters.outbox.purge.lock-at-least")
        .withMessageContaining("codehunters.outbox.purge.lock-at-most");
  }

  @Test
  void should_reject_purge_lock_at_most_when_not_positive() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new Purge(
                    Duration.ofDays(7),
                    500,
                    Duration.ofHours(1),
                    Duration.ZERO,
                    "purge",
                    Duration.ZERO,
                    Duration.ZERO))
        .withMessageContaining("codehunters.outbox.purge.lock-at-most");
  }

  @Test
  void should_reject_lock_at_most_when_budget_of_resolved_transport_exceeds_it() {
    EventMessagingProperties properties =
        scheduled(lockAtMost(Duration.ofSeconds(60)), SCHEDULING, publisher(null));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties.requireClaimBudget("rabbit"))
        .withMessageContaining(LOCK_KEY)
        .withMessageContaining("codehunters.outbox.publisher.rabbit.confirm-timeout");
  }

  @Test
  void should_reject_commit_grace_when_negative() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Claim(CLAIM_TTL, 10, Duration.ofSeconds(-1)))
        .withMessageContaining("codehunters.outbox.claim.commit-grace");
  }

  @Test
  void should_bound_dispatch_run_by_half_ttl_and_kafka_timeout_when_publisher_is_kafka() {
    EventMessagingProperties properties =
        properties(BATCH_SIZE, MAX_ATTEMPTS, claim(CLAIM_TTL), kafka());

    assertThat(properties.maxDispatchRun()).isEqualTo(CLAIM_TTL.dividedBy(2).plus(SEND_TIMEOUT));
  }

  @Test
  void should_bound_dispatch_run_by_longest_transport_timeout_when_publisher_type_unset() {
    EventMessagingProperties properties =
        new EventMessagingProperties(
            true,
            BATCH_SIZE,
            MAX_ATTEMPTS,
            TASK,
            claim(CLAIM_TTL),
            BACKOFF,
            new Publisher(null, new Kafka(SEND_TIMEOUT), new Rabbit(LONG_CONFIRM_TIMEOUT), false),
            PURGE,
            SCHEDULING);

    assertThat(properties.maxDispatchRun())
        .isEqualTo(CLAIM_TTL.dividedBy(2).plus(LONG_CONFIRM_TIMEOUT));
  }

  private static EventMessagingProperties scheduled(
      Task task, Scheduling scheduling, Publisher publisher) {
    return new EventMessagingProperties(
        true,
        BATCH_SIZE,
        MAX_ATTEMPTS,
        task,
        claim(CLAIM_TTL),
        BACKOFF,
        publisher,
        PURGE,
        scheduling);
  }

  private static Task lockAtMost(Duration lockAtMost) {
    return task(lockAtMost, Duration.ZERO);
  }

  private static Task task(Duration lockAtMost, Duration lockAtLeast) {
    return new Task(Duration.ofSeconds(5), lockAtMost, lockAtLeast, "outbox-app");
  }

  private static EventMessagingProperties properties(
      int batchSize, int maxAttempts, Claim claim, Publisher publisher) {
    return new EventMessagingProperties(
        true, batchSize, maxAttempts, TASK, claim, BACKOFF, publisher, PURGE, SCHEDULING);
  }

  private static Claim claim(Duration ttl) {
    return new Claim(ttl, 10, Duration.ZERO);
  }

  private static Purge purge(Duration retention, int batchSize) {
    return new Purge(
        retention,
        batchSize,
        Duration.ofHours(1),
        Duration.ofMinutes(1),
        "purge",
        Duration.ofMinutes(10),
        Duration.ofSeconds(30));
  }

  private static Purge purgeTiming(
      Duration fixedDelay, Duration initialDelay, Duration lockAtLeast) {
    return new Purge(
        Duration.ofDays(7),
        500,
        fixedDelay,
        initialDelay,
        "purge",
        Duration.ofMinutes(10),
        lockAtLeast);
  }

  private static Publisher publisher(String type) {
    return new Publisher(type, new Kafka(SEND_TIMEOUT), new Rabbit(CONFIRM_TIMEOUT), false);
  }

  private static Publisher rabbit() {
    return publisher("rabbit");
  }

  private static Publisher kafka() {
    return publisher("kafka");
  }
}
