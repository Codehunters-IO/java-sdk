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
  private static final long CLAIM_TTL_MS = 300_000L;
  private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);
  private static final long TTL_BELOW_BUDGET_MS = 250_000L;
  private static final Task TASK = new Task("5000", "300000", "10000", "outbox-app");
  private static final Scheduling SCHEDULING = new Scheduling(true);
  private static final String LOCK_KEY = "codehunters.outbox.task.lock-at-most";
  private static final Backoff BACKOFF = new Backoff(30_000L, 3_600_000L);
  private static final Purge PURGE =
      new Purge(Duration.ofDays(7), 500, "3600000", "60000", "purge", "PT10M", "PT30S");

  @Test
  void should_reject_batch_size_when_below_one() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties(0, MAX_ATTEMPTS, claim(CLAIM_TTL_MS), rabbit()))
        .withMessageContaining("codehunters.outbox.batch-size");
  }

  @Test
  void should_reject_max_attempts_when_below_one() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties(BATCH_SIZE, 0, claim(CLAIM_TTL_MS), rabbit()))
        .withMessageContaining("codehunters.outbox.max-attempts");
  }

  @Test
  void should_reject_claim_ttl_when_not_positive() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> claim(0))
        .withMessageContaining("codehunters.outbox.claim.ttl-ms");
  }

  @Test
  void should_reject_base_backoff_when_not_positive() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Backoff(0, 1000))
        .withMessageContaining("codehunters.outbox.backoff.base-delay-ms");
  }

  @Test
  void should_reject_max_backoff_when_not_positive() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Backoff(1000, -1))
        .withMessageContaining("codehunters.outbox.backoff.max-delay-ms");
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
        .isThrownBy(() -> properties(BATCH_SIZE, MAX_ATTEMPTS, claim(TTL_BELOW_BUDGET_MS), kafka()))
        .withMessageContaining("codehunters.outbox.claim.ttl-ms")
        .withMessageContaining("duplicate");
  }

  @Test
  void should_accept_kafka_publisher_when_claim_ttl_exceeds_batch_budget() {
    EventMessagingProperties properties =
        properties(BATCH_SIZE, MAX_ATTEMPTS, claim(CLAIM_TTL_MS), kafka());

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
        .isThrownBy(
            () -> properties(BATCH_SIZE, MAX_ATTEMPTS, claim(TTL_BELOW_BUDGET_MS), rabbit()))
        .withMessageContaining("codehunters.outbox.claim.ttl-ms")
        .withMessageContaining("codehunters.outbox.publisher.rabbit.confirm-timeout")
        .withMessageContaining("duplicate");
  }

  @Test
  void should_accept_rabbit_publisher_when_claim_ttl_exceeds_batch_budget() {
    EventMessagingProperties properties =
        properties(BATCH_SIZE, MAX_ATTEMPTS, claim(CLAIM_TTL_MS), rabbit());

    assertThat(properties.publisher().type()).isEqualTo("rabbit");
  }

  @Test
  void should_ignore_kafka_send_timeout_when_publisher_is_rabbit() {
    Publisher publisher =
        new Publisher("rabbit", new Kafka(Duration.ofHours(1)), new Rabbit(CONFIRM_TIMEOUT));

    EventMessagingProperties properties =
        properties(BATCH_SIZE, MAX_ATTEMPTS, claim(CLAIM_TTL_MS), publisher);

    assertThat(properties.claim().ttlMs()).isEqualTo(CLAIM_TTL_MS);
  }

  @Test
  void should_defer_claim_budget_when_publisher_type_unset() {
    EventMessagingProperties properties =
        properties(BATCH_SIZE, MAX_ATTEMPTS, claim(1_000L), publisher(null));

    assertThat(properties.publisher().type()).isNull();
  }

  @Test
  void should_reject_claim_ttl_when_budget_of_resolved_rabbit_transport_exceeds_it() {
    EventMessagingProperties properties =
        properties(BATCH_SIZE, MAX_ATTEMPTS, claim(1_000L), publisher(null));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties.requireClaimBudget("rabbit"))
        .withMessageContaining("codehunters.outbox.publisher.rabbit.confirm-timeout");
  }

  @Test
  void should_reject_claim_ttl_when_budget_of_resolved_kafka_transport_exceeds_it() {
    EventMessagingProperties properties =
        properties(BATCH_SIZE, MAX_ATTEMPTS, claim(1_000L), publisher(null));

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
            new Rabbit(CONFIRM_TIMEOUT));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties(BATCH_SIZE, MAX_ATTEMPTS, claim(CLAIM_TTL_MS), publisher))
        .withMessageContaining("codehunters.outbox.publisher.kafka.send-timeout");
  }

  @Test
  void should_reject_confirm_timeout_when_it_overflows_milliseconds() {
    Publisher publisher =
        new Publisher(
            "rabbit", new Kafka(SEND_TIMEOUT), new Rabbit(Duration.ofSeconds(Long.MAX_VALUE)));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties(BATCH_SIZE, MAX_ATTEMPTS, claim(CLAIM_TTL_MS), publisher))
        .withMessageContaining("codehunters.outbox.publisher.rabbit.confirm-timeout");
  }

  @Test
  void should_accept_resolved_transport_when_claim_ttl_exceeds_its_budget() {
    EventMessagingProperties properties =
        properties(BATCH_SIZE, MAX_ATTEMPTS, claim(CLAIM_TTL_MS), publisher(null));

    properties.requireClaimBudget("kafka");

    assertThat(properties.claim().ttlMs()).isEqualTo(CLAIM_TTL_MS);
  }

  @Test
  void should_reject_lock_at_most_naming_keys_when_shorter_than_dispatch_run() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> scheduled(lockAtMost("60000"), SCHEDULING, kafka()))
        .withMessageContaining(LOCK_KEY)
        .withMessageContaining("codehunters.outbox.claim.ttl-ms")
        .withMessageContaining("codehunters.outbox.publisher.kafka.send-timeout");
  }

  @Test
  void should_accept_lock_at_most_when_iso_duration_exceeds_dispatch_run() {
    EventMessagingProperties properties = scheduled(lockAtMost("PT3M"), SCHEDULING, rabbit());

    assertThat(properties.task().lockAtMost()).isEqualTo("PT3M");
  }

  @Test
  void should_accept_lock_at_most_when_simple_duration_exceeds_dispatch_run() {
    EventMessagingProperties properties = scheduled(lockAtMost("4m"), SCHEDULING, kafka());

    assertThat(properties.task().lockAtMost()).isEqualTo("4m");
  }

  @Test
  void should_skip_lock_check_when_scheduling_disabled() {
    EventMessagingProperties properties =
        scheduled(lockAtMost("60000"), new Scheduling(false), kafka());

    assertThat(properties.scheduling().enabled()).isFalse();
  }

  @Test
  void should_reject_lock_at_most_naming_key_when_not_a_duration() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> scheduled(lockAtMost("soon"), SCHEDULING, kafka()))
        .withMessageContaining(LOCK_KEY);
  }

  @Test
  void should_reject_lock_at_most_when_budget_of_resolved_transport_exceeds_it() {
    EventMessagingProperties properties =
        scheduled(lockAtMost("60000"), SCHEDULING, publisher(null));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties.requireClaimBudget("rabbit"))
        .withMessageContaining(LOCK_KEY)
        .withMessageContaining("codehunters.outbox.publisher.rabbit.confirm-timeout");
  }

  private static EventMessagingProperties scheduled(
      Task task, Scheduling scheduling, Publisher publisher) {
    return new EventMessagingProperties(
        true,
        BATCH_SIZE,
        MAX_ATTEMPTS,
        task,
        claim(CLAIM_TTL_MS),
        BACKOFF,
        publisher,
        PURGE,
        scheduling);
  }

  private static Task lockAtMost(String lockAtMost) {
    return new Task("5000", lockAtMost, "10000", "outbox-app");
  }

  private static EventMessagingProperties properties(
      int batchSize, int maxAttempts, Claim claim, Publisher publisher) {
    return new EventMessagingProperties(
        true, batchSize, maxAttempts, TASK, claim, BACKOFF, publisher, PURGE, SCHEDULING);
  }

  private static Claim claim(long ttlMs) {
    return new Claim(ttlMs);
  }

  private static Purge purge(Duration retention, int batchSize) {
    return new Purge(retention, batchSize, "3600000", "60000", "purge", "PT10M", "PT30S");
  }

  private static Publisher publisher(String type) {
    return new Publisher(type, new Kafka(SEND_TIMEOUT), new Rabbit(CONFIRM_TIMEOUT));
  }

  private static Publisher rabbit() {
    return publisher("rabbit");
  }

  private static Publisher kafka() {
    return publisher("kafka");
  }
}
