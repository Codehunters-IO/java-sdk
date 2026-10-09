package io.codehunters.messaging.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.MessagePublisherPort;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.OutboxWriter;
import io.codehunters.messaging.config.EventMessagingCoreAutoConfiguration;
import io.codehunters.messaging.jdbc.config.EventMessagingJdbcAutoConfiguration;
import io.codehunters.messaging.jdbc.config.OutboxDispatchSchedulerAutoConfiguration;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The auto-configured outbox dispatches a committed message right away, long before the next poll,
 * and wiring the commit-time trigger into the store creates no bean cycle.
 */
class DispatchOnCommitContextIT {

  private static final Logger log = LoggerFactory.getLogger(DispatchOnCommitContextIT.class);
  private static final int COMMITS = 50;
  private static final Duration PUBLISH_WAIT = Duration.ofSeconds(5);

  private final BlockingQueue<UUID> published = new LinkedBlockingQueue<>();
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  EventMessagingJdbcAutoConfiguration.class,
                  EventMessagingCoreAutoConfiguration.class,
                  OutboxDispatchSchedulerAutoConfiguration.class))
          .withPropertyValues(
              "spring.application.name=sentinel-ms-test", "codehunters.outbox.task.fixed-delay=1h")
          .withBean(DataSource.class, DispatchOnCommitContextIT::unclosablePool)
          .withBean(MessagePublisherPort.class, () -> new QueuePublisher(published));

  // The shared pool outlives each context, which would close a bean that has a close method.
  private static DataSource unclosablePool() {
    return new DelegatingDataSource(PostgresTestSupport.pooledDataSource());
  }

  @BeforeEach
  void setUp() {
    PostgresTestSupport.truncate();
  }

  @Test
  void should_publish_committed_message_before_next_poll_when_dispatch_on_commit_enabled() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          UUID id = commit(context);

          assertThat(published.poll(PUBLISH_WAIT.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo(id);
        });
  }

  @Test
  void should_publish_each_commit_within_milliseconds_when_commits_follow_each_other() {
    contextRunner.run(
        context -> {
          long totalNanos = commitAndAwaitEach(context);

          log.info(
              "Commit-to-publish latency over {} commits: average {} us",
              COMMITS,
              TimeUnit.NANOSECONDS.toMicros(totalNanos / COMMITS));
          assertThat(Duration.ofNanos(totalNanos / COMMITS)).isLessThan(Duration.ofSeconds(1));
        });
  }

  private long commitAndAwaitEach(AssertableApplicationContext context) throws Exception {
    long total = 0;
    for (int i = 0; i < COMMITS; i++) {
      long start = System.nanoTime();
      UUID id = commit(context);
      assertThat(published.poll(PUBLISH_WAIT.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo(id);
      total += System.nanoTime() - start;
    }
    return total;
  }

  private static UUID commit(AssertableApplicationContext context) {
    UUID id = UUID.randomUUID();
    OutboxWriter writer = context.getBean(OutboxWriter.class);
    new TransactionTemplate(new DataSourceTransactionManager(context.getBean(DataSource.class)))
        .executeWithoutResult(
            status ->
                writer.save(
                    OutboxMessage.<String>builder()
                        .id(id)
                        .topic("t")
                        .key("k")
                        .message("m")
                        .build()));
    return id;
  }

  /** Publisher that hands every published id to the test. */
  private record QueuePublisher(BlockingQueue<UUID> published) implements MessagePublisherPort {

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
      published.add(message.id());
    }
  }
}
