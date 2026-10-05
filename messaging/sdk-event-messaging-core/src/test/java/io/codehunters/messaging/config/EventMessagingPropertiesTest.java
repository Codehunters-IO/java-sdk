package io.codehunters.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.codehunters.messaging.config.EventMessagingProperties.Backoff;
import io.codehunters.messaging.config.EventMessagingProperties.Claim;
import io.codehunters.messaging.config.EventMessagingProperties.Kafka;
import io.codehunters.messaging.config.EventMessagingProperties.Publisher;
import io.codehunters.messaging.config.EventMessagingProperties.Purge;
import io.codehunters.messaging.config.EventMessagingProperties.Task;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class EventMessagingPropertiesTest {

  private static final int BATCH_SIZE = 50;
  private static final int MAX_ATTEMPTS = 5;
  private static final long CLAIM_TTL_MS = 300_000L;
  private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);
  private static final Task TASK = new Task("5000", "60000", "10000", "outbox-app");
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
        .isThrownBy(() -> new Publisher("sqs", new Kafka(SEND_TIMEOUT)))
        .withMessageContaining("codehunters.outbox.publisher.type")
        .withMessageContaining("sqs");
  }

  @Test
  void should_reject_publisher_type_when_missing() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Publisher(null, new Kafka(SEND_TIMEOUT)))
        .withMessageContaining("codehunters.outbox.publisher.type");
  }

  @Test
  void should_accept_publisher_type_ignoring_case_when_known() {
    Publisher publisher = new Publisher("KaFkA", new Kafka(SEND_TIMEOUT));

    assertThat(publisher.type()).isEqualTo("kafka");
  }

  @Test
  void should_reject_claim_ttl_explaining_duplicates_when_kafka_batch_budget_exceeds_it() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> properties(BATCH_SIZE, MAX_ATTEMPTS, claim(250_000L), kafka()))
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
  void should_ignore_kafka_budget_when_publisher_is_rabbit() {
    EventMessagingProperties properties =
        properties(BATCH_SIZE, MAX_ATTEMPTS, claim(1_000L), rabbit());

    assertThat(properties.claim().ttlMs()).isEqualTo(1_000L);
  }

  private static EventMessagingProperties properties(
      int batchSize, int maxAttempts, Claim claim, Publisher publisher) {
    return new EventMessagingProperties(
        true, batchSize, maxAttempts, TASK, claim, BACKOFF, publisher, PURGE);
  }

  private static Claim claim(long ttlMs) {
    return new Claim(ttlMs, "auto");
  }

  private static Purge purge(Duration retention, int batchSize) {
    return new Purge(retention, batchSize, "3600000", "60000", "purge", "PT10M", "PT30S");
  }

  private static Publisher rabbit() {
    return new Publisher("rabbit", new Kafka(SEND_TIMEOUT));
  }

  private static Publisher kafka() {
    return new Publisher("kafka", new Kafka(SEND_TIMEOUT));
  }
}
