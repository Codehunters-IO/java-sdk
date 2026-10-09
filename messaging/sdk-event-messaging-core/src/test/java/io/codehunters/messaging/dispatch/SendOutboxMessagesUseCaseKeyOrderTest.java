package io.codehunters.messaging.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.ClaimRequest;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.OutboxStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SendOutboxMessagesUseCaseKeyOrderTest {

  private static final int BATCH_SIZE = 4;
  private static final int MAX_ATTEMPTS = 3;
  private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
  private static final String CLAIM_MARKER = "instance-1:marker";
  private static final String ORDERS = "orders";
  private static final String PAYMENTS = "payments";

  private FakeDispatchStore store;
  private RecordingPublisher publisher;
  private SendOutboxMessagesUseCase useCase;

  @BeforeEach
  void setUp() {
    MutableClock clock = new MutableClock(NOW);
    List<String> calls = new ArrayList<>();
    store = new FakeDispatchStore(clock, calls);
    publisher = new RecordingPublisher(clock, calls);
    DispatchSettings settings =
        new DispatchSettings(
            new ClaimRequest("origin", "instance-1", BATCH_SIZE, Duration.ofMinutes(5), 10),
            new RetryPolicy(MAX_ATTEMPTS, Duration.ofSeconds(30), Duration.ofHours(1)),
            clock);
    useCase = new SendOutboxMessagesUseCase(store, publisher, settings);
  }

  @Test
  void should_release_later_rows_of_key_without_publishing_when_publish_fails() {
    OutboxMessage<String> a1 = claimed(ORDERS, "A", 0);
    OutboxMessage<String> a2 = claimed(ORDERS, "A", 0);
    OutboxMessage<String> a3 = claimed(ORDERS, "A", 0);
    store.willClaim(List.of(a1, a2, a3));
    publisher.failFor(a1);

    useCase.execute();

    assertThat(publisher.published()).isEmpty();
    assertThat(store.startedIds()).containsExactly(a1.id());
    assertThat(store.updatedIds()).containsExactly(a1.id(), a2.id(), a3.id());
    assertThat(store.updated().getFirst().nextAttemptAt()).isAfter(NOW);
    assertReleased(store.updated().get(1));
    assertReleased(store.updated().get(2));
  }

  @Test
  void should_keep_publishing_other_keys_when_one_key_fails() {
    OutboxMessage<String> a1 = claimed(ORDERS, "A", 0);
    OutboxMessage<String> b1 = claimed(ORDERS, "B", 0);
    OutboxMessage<String> a2 = claimed(ORDERS, "A", 0);
    OutboxMessage<String> b2 = claimed(ORDERS, "B", 0);
    store.willClaim(List.of(a1, b1, a2, b2));
    publisher.failFor(a1);

    useCase.execute();

    assertThat(publisher.publishedIds()).containsExactly(b1.id(), b2.id());
  }

  @Test
  void should_keep_publishing_same_key_on_other_topic_when_one_topic_fails() {
    OutboxMessage<String> orderA = claimed(ORDERS, "A", 0);
    OutboxMessage<String> paymentA = claimed(PAYMENTS, "A", 0);
    store.willClaim(List.of(orderA, paymentA));
    publisher.failFor(orderA);

    useCase.execute();

    assertThat(publisher.publishedIds()).containsExactly(paymentA.id());
  }

  @Test
  void should_publish_later_null_key_rows_when_earlier_null_key_row_fails() {
    OutboxMessage<String> n1 = claimed(ORDERS, null, 0);
    OutboxMessage<String> n2 = claimed(ORDERS, null, 0);
    store.willClaim(List.of(n1, n2));
    publisher.failFor(n1);

    useCase.execute();

    assertThat(publisher.publishedIds()).containsExactly(n2.id());
  }

  @Test
  void should_release_later_rows_of_key_when_head_mark_is_refused() {
    OutboxMessage<String> a1 = claimed(ORDERS, "A", 0);
    OutboxMessage<String> a2 = claimed(ORDERS, "A", 0);
    store.willClaim(List.of(a1, a2));
    store.refuseMark(a1);

    useCase.execute();

    assertThat(publisher.published()).isEmpty();
    assertThat(store.updatedIds()).containsExactly(a2.id());
    assertReleased(store.updated().getFirst());
  }

  @Test
  void should_release_later_rows_of_key_when_head_is_marked_failed() {
    OutboxMessage<String> a1 = claimed(ORDERS, "A", MAX_ATTEMPTS);
    OutboxMessage<String> a2 = claimed(ORDERS, "A", 0);
    store.willClaim(List.of(a1, a2));

    useCase.execute();

    assertThat(publisher.published()).isEmpty();
    assertThat(store.updated().getFirst().status()).isEqualTo(OutboxStatus.FAILED);
    assertReleased(store.updated().get(1));
  }

  @Test
  void should_publish_every_row_of_key_in_order_when_all_succeed() {
    OutboxMessage<String> a1 = claimed(ORDERS, "A", 0);
    OutboxMessage<String> a2 = claimed(ORDERS, "A", 0);
    OutboxMessage<String> a3 = claimed(ORDERS, "A", 0);
    store.willClaim(List.of(a1, a2, a3));

    useCase.execute();

    assertThat(publisher.publishedIds()).containsExactly(a1.id(), a2.id(), a3.id());
  }

  @Test
  void should_report_more_waiting_when_claim_came_back_full() {
    store.willClaim(Collections.nCopies(BATCH_SIZE, claimed(ORDERS, null, 0)));

    assertThat(useCase.execute()).isTrue();
  }

  @Test
  void should_report_nothing_waiting_when_claim_came_back_short() {
    store.willClaim(List.of(claimed(ORDERS, null, 0)));

    assertThat(useCase.execute()).isFalse();
  }

  @Test
  void should_report_nothing_waiting_when_claim_came_back_empty() {
    assertThat(useCase.execute()).isFalse();
  }

  private static void assertReleased(OutboxMessage<String> message) {
    assertThat(message.status()).isEqualTo(OutboxStatus.PENDING);
    assertThat(message.attempts()).isZero();
    assertThat(message.nextAttemptAt()).isEqualTo(NOW);
    assertThat(message.claimedBy()).isEqualTo(CLAIM_MARKER);
  }

  private static OutboxMessage<String> claimed(String topic, String key, int attempts) {
    return OutboxMessage.<String>builder()
        .id(UUID.randomUUID())
        .origin("origin")
        .topic(topic)
        .key(key)
        .message("payload")
        .status(OutboxStatus.IN_FLIGHT)
        .attempts(attempts)
        .claimedAt(NOW)
        .claimedBy(CLAIM_MARKER)
        .build();
  }
}
