package io.codehunters.messaging.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.ClaimRequest;
import io.codehunters.messaging.OutboxDispatchTrigger;
import io.codehunters.messaging.OutboxMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class OutboxClaimConcurrencyIT {

  private static final String ORIGIN = "sentinel-ms-test";
  private static final int ROWS = 400;
  private static final int WORKERS = 8;
  private static final int BATCH = 25;
  private static final Duration TTL = Duration.ofMinutes(5);
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-05T10:00:00Z"), ZoneOffset.UTC);

  private DataSource dataSource;
  private OutboxJdbcAdapter adapter;

  @BeforeEach
  void setUp() {
    dataSource = PostgresTestSupport.dataSource();
    PostgresTestSupport.truncate();
    var settings = new OutboxAdapterSettings(ORIGIN, CLOCK, OutboxDispatchTrigger.none());
    adapter = new OutboxJdbcAdapter(dataSource, JsonMapper.builder().build(), settings);
  }

  @Test
  void should_claim_disjoint_sets_covering_every_row_when_eight_workers_race() throws Exception {
    saveRowsWithDistinctKeys();

    List<List<UUID>> perWorker = runWorkers();

    var all = perWorker.stream().flatMap(List::stream).toList();
    assertThat(all).hasSize(ROWS);
    assertThat(new HashSet<>(all)).hasSize(ROWS);
  }

  private void saveRowsWithDistinctKeys() {
    var tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    tx.executeWithoutResult(
        status ->
            IntStream.range(0, ROWS)
                .forEach(
                    i ->
                        adapter.save(
                            OutboxMessage.<String>builder()
                                .topic("t")
                                .key("k-" + i)
                                .message("m-" + i)
                                .build())));
  }

  private List<List<UUID>> runWorkers() throws Exception {
    var startGate = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
    try {
      var futures = new ArrayList<Future<List<UUID>>>();
      for (int w = 0; w < WORKERS; w++) {
        String worker = "w" + w;
        futures.add(pool.submit(() -> drain(worker, startGate)));
      }
      startGate.countDown();
      var results = new ArrayList<List<UUID>>();
      for (var future : futures) {
        results.add(future.get());
      }
      return results;
    } finally {
      pool.shutdownNow();
    }
  }

  private List<UUID> drain(String worker, CountDownLatch startGate) throws InterruptedException {
    startGate.await();
    var claimed = new ArrayList<UUID>();
    List<OutboxMessage<String>> batch =
        adapter.claim(new ClaimRequest(ORIGIN, worker, BATCH, TTL, 10));
    while (!batch.isEmpty()) {
      batch.forEach(message -> claimed.add(message.id()));
      batch = adapter.claim(new ClaimRequest(ORIGIN, worker, BATCH, TTL, 10));
    }
    return claimed;
  }
}
