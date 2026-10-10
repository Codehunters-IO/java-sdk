package io.codehunters.messaging.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.ClaimRequest;
import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxDispatchTrigger;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.dispatch.DispatchSettings;
import io.codehunters.messaging.dispatch.RetryPolicy;
import io.codehunters.messaging.dispatch.SendOutboxMessagesUseCase;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Drains a keyed backlog with per-key batching on and off. The broker is an in-memory list, so the
 * numbers are the database-bound ceiling of one dispatcher; a real broker adds its ack latency per
 * message.
 */
class OutboxDispatchThroughputIT {

  private static final Logger log = LoggerFactory.getLogger(OutboxDispatchThroughputIT.class);
  private static final String ORIGIN = "sentinel-ms-test";
  private static final int KEYS = 20;
  private static final int ROWS_PER_KEY = 100;
  private static final int BATCH_SIZE = 50;
  private static final int MAX_RUNS = 10_000;

  private DataSource dataSource;
  private OutboxJdbcAdapter adapter;

  @BeforeEach
  void setUp() {
    dataSource = PostgresTestSupport.pooledDataSource();
    PostgresTestSupport.truncate();
    var settings =
        new OutboxAdapterSettings(ORIGIN, Clock.systemUTC(), OutboxDispatchTrigger.none());
    adapter = new OutboxJdbcAdapter(dataSource, JsonMapper.builder().build(), settings);
  }

  @Test
  void should_drain_keyed_backlog_in_fewer_runs_when_several_rows_per_key_are_claimed() {
    DrainResult oneRowPerKey = drainBacklog(1);
    PostgresTestSupport.truncate();
    DrainResult tenRowsPerKey = drainBacklog(10);

    assertThat(oneRowPerKey.published()).isEqualTo(KEYS * ROWS_PER_KEY);
    assertThat(tenRowsPerKey.published()).isEqualTo(KEYS * ROWS_PER_KEY);
    assertThat(tenRowsPerKey.runs()).isLessThan(oneRowPerKey.runs());
  }

  private DrainResult drainBacklog(int maxPerKey) {
    saveBacklog();
    List<OutboxMessage<String>> published = Collections.synchronizedList(new ArrayList<>());
    SendOutboxMessagesUseCase useCase = useCase(maxPerKey, new ListPublisher(published));
    long start = System.nanoTime();
    int runs = runUntilPublished(useCase, published);
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    log.info(
        "max-per-key={}: {} messages in {} runs, {} ms, {} msg/s",
        maxPerKey,
        published.size(),
        runs,
        elapsedMs,
        published.size() * 1000L / Math.max(1, elapsedMs));
    return new DrainResult(published.size(), runs);
  }

  // A short claim ends a scheduled drain; here every run is counted until the backlog is gone.
  private static int runUntilPublished(
      SendOutboxMessagesUseCase useCase, List<OutboxMessage<String>> published) {
    int runs = 0;
    while (published.size() < KEYS * ROWS_PER_KEY && runs < MAX_RUNS) {
      useCase.execute();
      runs++;
    }
    return runs;
  }

  private SendOutboxMessagesUseCase useCase(int maxPerKey, MessagePublisherPort publisher) {
    var claim = new ClaimRequest(ORIGIN, "bench", BATCH_SIZE, Duration.ofMinutes(5), maxPerKey);
    var retry = new RetryPolicy(5, Duration.ofSeconds(30), Duration.ofHours(1));
    return new SendOutboxMessagesUseCase(
        adapter, publisher, new DispatchSettings(claim, retry, Clock.systemUTC()));
  }

  private void saveBacklog() {
    new TransactionTemplate(new DataSourceTransactionManager(dataSource))
        .executeWithoutResult(
            status ->
                IntStream.range(0, KEYS * ROWS_PER_KEY)
                    .forEach(
                        i ->
                            adapter.save(
                                OutboxMessage.<String>builder()
                                    .topic("t")
                                    .key("k" + i % KEYS)
                                    .message("m" + i)
                                    .build())));
  }

  private record DrainResult(int published, int runs) {}

  /** Broker stand-in that only records what it was handed. */
  private record ListPublisher(List<OutboxMessage<String>> published)
      implements MessagePublisherPort {

    @Override
    public void publish(String topic, String routingKey, String payload) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void publish(String topic, String payload) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void publish(OutboxMessage<String> message) {
      published.add(message);
    }
  }
}
