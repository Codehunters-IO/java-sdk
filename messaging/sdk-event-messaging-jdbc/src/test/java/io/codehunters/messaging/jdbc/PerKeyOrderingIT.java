package io.codehunters.messaging.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.ClaimRequest;
import io.codehunters.messaging.OutboxDispatchTrigger;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.OutboxStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PerKeyOrderingIT {

  private static final String ORIGIN = "sentinel-ms-test";
  private static final String MAIN = "t-main";
  private static final String OTHER = "t-other";
  private static final Duration TTL = Duration.ofMinutes(5);
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-05T10:00:00Z"), ZoneOffset.UTC);

  private OutboxJdbcAdapter adapter;

  @BeforeEach
  void setUp() {
    PostgresTestSupport.truncate();
    var settings = new OutboxAdapterSettings(ORIGIN, CLOCK, OutboxDispatchTrigger.none());
    adapter =
        new OutboxJdbcAdapter(
            PostgresTestSupport.dataSource(), JsonMapper.builder().build(), settings);
  }

  @Test
  void should_hold_later_same_key_row_when_earlier_row_failed() {
    var a1 = save(MAIN, "A", "A1");
    save(MAIN, "A", "A2");
    save(MAIN, "B", "B1");
    setStatus(a1, OutboxStatus.FAILED);

    assertThat(payloads(claim("w1"))).containsExactly("B1");
  }

  @Test
  void should_release_key_on_other_topic_when_failure_is_on_a_different_topic() {
    var blocker = save(OTHER, "A", "A-other");
    save(MAIN, "A", "A-main");
    setStatus(blocker, OutboxStatus.FAILED);

    assertThat(payloads(claim("w1"))).containsExactly("A-main");
  }

  @Test
  void should_claim_requeued_head_and_not_its_successor_when_failed_row_is_requeued() {
    var a1 = save(MAIN, "A", "A1");
    save(MAIN, "A", "A2");
    setStatus(a1, OutboxStatus.FAILED);
    setStatus(a1, OutboxStatus.PENDING);

    assertThat(payloads(claim("w1"))).containsExactly("A1");
  }

  @Test
  void should_not_hand_successor_to_concurrent_claimers_when_head_is_in_flight() throws Exception {
    save(MAIN, "A", "A1");
    save(MAIN, "A", "A2");
    assertThat(payloads(adapter.claim(new ClaimRequest(ORIGIN, "w0", 1, TTL, 1))))
        .containsExactly("A1");

    var results = claimConcurrently("w1", "w2");

    assertThat(results).allSatisfy(batch -> assertThat(batch).isEmpty());
  }

  @Test
  void should_claim_null_key_rows_regardless_of_order_when_earlier_null_key_row_failed() {
    var n1 = save(MAIN, null, "N1");
    save(MAIN, null, "N2");
    setStatus(n1, OutboxStatus.FAILED);

    assertThat(payloads(claim("w1"))).containsExactly("N2");
  }

  private List<OutboxMessage<String>> claim(String worker) {
    return adapter.claim(new ClaimRequest(ORIGIN, worker, 10, TTL, 1));
  }

  private List<List<OutboxMessage<String>>> claimConcurrently(String... workers) throws Exception {
    var startGate = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(workers.length);
    try {
      var futures = new ArrayList<Future<List<OutboxMessage<String>>>>();
      for (String worker : workers) {
        Callable<List<OutboxMessage<String>>> task =
            () -> {
              startGate.await();
              return claim(worker);
            };
        futures.add(pool.submit(task));
      }
      startGate.countDown();
      var results = new ArrayList<List<OutboxMessage<String>>>();
      for (var future : futures) {
        results.add(future.get());
      }
      return results;
    } finally {
      pool.shutdownNow();
    }
  }

  private UUID save(String topic, String key, String payload) {
    var id = UUID.randomUUID();
    adapter.save(
        OutboxMessage.<String>builder().id(id).topic(topic).key(key).message(payload).build());
    return id;
  }

  private void setStatus(UUID id, OutboxStatus status) {
    adapter.update(
        OutboxMessage.<String>builder()
            .id(id)
            .topic("ignored")
            .message("ignored")
            .status(status)
            .build());
  }

  private static List<String> payloads(List<OutboxMessage<String>> claimed) {
    return claimed.stream().map(OutboxMessage::message).toList();
  }
}
