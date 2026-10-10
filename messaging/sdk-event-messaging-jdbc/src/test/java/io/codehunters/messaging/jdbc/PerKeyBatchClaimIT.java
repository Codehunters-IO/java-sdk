package io.codehunters.messaging.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.ClaimRequest;
import io.codehunters.messaging.OutboxDispatchTrigger;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.OutboxStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Per-key batching of the PostgreSQL claim: several contiguous rows of one key per claim. */
class PerKeyBatchClaimIT {

  private static final String ORIGIN = "sentinel-ms-test";
  private static final String TOPIC = "t-main";
  private static final Duration TTL = Duration.ofMinutes(5);
  private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final int RACE_KEYS = 20;
  private static final int RACE_ROWS_PER_KEY = 25;
  private static final int RACE_WORKERS = 8;

  private DataSource dataSource;
  private OutboxJdbcAdapter adapter;

  @BeforeEach
  void setUp() {
    dataSource = PostgresTestSupport.dataSource();
    PostgresTestSupport.truncate();
    adapter = adapterAt(CLOCK);
  }

  @Test
  void should_claim_contiguous_rows_of_key_in_seq_order_when_key_has_more_than_max_per_key() {
    saveAll("A1", "A2", "A3", "A4", "A5");
    save("B", "B1");
    save("B", "B2");

    assertThat(payloads(claim("w1", 10, 3))).containsExactly("A1", "A2", "A3", "B1", "B2");
  }

  @Test
  void should_claim_successors_in_next_claim_when_earlier_rows_are_published() {
    saveAll("A1", "A2", "A3", "A4", "A5");
    claim("w1", 10, 3).forEach(message -> adapter.update(message.published(NOW)));

    assertThat(payloads(claim("w2", 10, 3))).containsExactly("A4", "A5");
  }

  @Test
  void should_claim_nothing_of_key_when_earlier_row_failed() {
    UUID head = save("A", "A1");
    save("A", "A2");
    save("B", "B1");
    setState(head, OutboxStatus.FAILED, null);

    assertThat(payloads(claim("w1", 10, 10))).containsExactly("B1");
  }

  @Test
  void should_claim_nothing_of_key_when_earlier_row_is_not_due() {
    UUID head = save("A", "A1");
    save("A", "A2");
    save("B", "B1");
    setState(head, OutboxStatus.PENDING, NOW.plus(Duration.ofMinutes(1)));

    assertThat(payloads(claim("w1", 10, 10))).containsExactly("B1");
  }

  @Test
  void should_claim_nothing_of_key_when_earlier_row_is_freshly_in_flight() {
    saveAll("A1", "A2", "A3");
    save("B", "B1");
    assertThat(payloads(claim("w0", 1, 1))).containsExactly("A1");

    assertThat(payloads(claim("w1", 10, 10))).containsExactly("B1");
  }

  @Test
  void should_reclaim_stale_head_together_with_its_successors_when_claim_expired() {
    saveAll("A1", "A2", "A3");
    adapterAt(Clock.fixed(NOW.minus(TTL.multipliedBy(2)), ZoneOffset.UTC))
        .claim(new ClaimRequest(ORIGIN, "dead", 1, TTL, 1));

    assertThat(payloads(claim("w1", 10, 10))).containsExactly("A1", "A2", "A3");
  }

  @Test
  void should_claim_one_row_per_key_when_max_per_key_is_one() {
    saveAll("A1", "A2");
    save("B", "B1");
    save("B", "B2");

    assertThat(payloads(claim("w1", 10, 1))).containsExactly("A1", "B1");
  }

  @Test
  void should_claim_every_null_key_row_when_max_per_key_is_one() {
    save(null, "N1");
    save(null, "N2");

    assertThat(payloads(claim("w1", 10, 1))).containsExactly("N1", "N2");
  }

  @Test
  void should_hold_back_successors_when_earlier_row_is_locked_by_concurrent_claimer()
      throws Exception {
    UUID head = save("A", "A1");
    save("A", "A2");
    save("A", "A3");
    save("B", "B1");

    try (Connection concurrent = dataSource.getConnection()) {
      lockRow(concurrent, head);
      assertThat(payloads(claim("w1", 10, 10))).containsExactly("B1");
      concurrent.rollback();
    }
  }

  @Test
  void should_never_split_a_key_out_of_order_when_workers_race() throws Exception {
    saveRaceRows();
    List<String> published = Collections.synchronizedList(new ArrayList<>());

    raceWorkers(published);

    assertThat(published).hasSize(RACE_KEYS * RACE_ROWS_PER_KEY);
    assertThat(byKey(published))
        .allSatisfy((key, sequence) -> assertThat(sequence).isSortedAccordingTo(Integer::compare));
    assertThat(byKey(published))
        .allSatisfy((key, sequence) -> assertThat(sequence).doesNotHaveDuplicates());
  }

  private void raceWorkers(List<String> published) throws Exception {
    var startGate = new CountDownLatch(1);
    var remaining = new AtomicInteger(RACE_KEYS * RACE_ROWS_PER_KEY);
    ExecutorService pool = Executors.newFixedThreadPool(RACE_WORKERS);
    try {
      var futures = new ArrayList<Future<?>>();
      IntStream.range(0, RACE_WORKERS)
          .forEach(
              w -> futures.add(pool.submit(() -> race("w" + w, startGate, remaining, published))));
      startGate.countDown();
      for (Future<?> future : futures) {
        future.get();
      }
    } finally {
      pool.shutdownNow();
    }
  }

  // Publishes in claim order and records each row just before marking it PUBLISHED, as the
  // dispatcher does: a successor can only be claimed once its predecessor's update committed.
  private Void race(
      String worker, CountDownLatch startGate, AtomicInteger remaining, List<String> published)
      throws InterruptedException {
    startGate.await();
    long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
    while (remaining.get() > 0 && System.nanoTime() < deadline) {
      for (OutboxMessage<String> message : claim(worker, 7, 5)) {
        published.add(message.message());
        adapter.update(message.published(NOW));
        remaining.decrementAndGet();
      }
    }
    return null;
  }

  private void saveRaceRows() {
    IntStream.range(0, RACE_ROWS_PER_KEY)
        .forEach(i -> IntStream.range(0, RACE_KEYS).forEach(k -> save("k" + k, "k" + k + ":" + i)));
  }

  private static Map<String, List<Integer>> byKey(List<String> published) {
    return published.stream()
        .map(payload -> payload.split(":"))
        .collect(
            Collectors.groupingBy(
                parts -> parts[0],
                Collectors.mapping(parts -> Integer.valueOf(parts[1]), Collectors.toList())));
  }

  private static void lockRow(Connection connection, UUID id) throws SQLException {
    connection.setAutoCommit(false);
    try (PreparedStatement lock =
        connection.prepareStatement("SELECT id FROM outbox_message WHERE id = ? FOR UPDATE")) {
      lock.setObject(1, id);
      lock.executeQuery().close();
    }
  }

  private OutboxJdbcAdapter adapterAt(Clock clock) {
    var settings = new OutboxAdapterSettings(ORIGIN, clock, OutboxDispatchTrigger.none());
    return new OutboxJdbcAdapter(dataSource, JsonMapper.builder().build(), settings);
  }

  private List<OutboxMessage<String>> claim(String worker, int batchSize, int maxPerKey) {
    return adapter.claim(new ClaimRequest(ORIGIN, worker, batchSize, TTL, maxPerKey));
  }

  private void saveAll(String... payloads) {
    List.of(payloads).forEach(payload -> save(payload.substring(0, 1), payload));
  }

  private UUID save(String key, String payload) {
    var id = UUID.randomUUID();
    adapter.save(
        OutboxMessage.<String>builder().id(id).topic(TOPIC).key(key).message(payload).build());
    return id;
  }

  private void setState(UUID id, OutboxStatus status, Instant nextAttemptAt) {
    adapter.update(
        OutboxMessage.<String>builder()
            .id(id)
            .topic("ignored")
            .message("ignored")
            .status(status)
            .nextAttemptAt(nextAttemptAt)
            .build());
  }

  private static List<String> payloads(List<OutboxMessage<String>> claimed) {
    return claimed.stream().map(OutboxMessage::message).toList();
  }
}
