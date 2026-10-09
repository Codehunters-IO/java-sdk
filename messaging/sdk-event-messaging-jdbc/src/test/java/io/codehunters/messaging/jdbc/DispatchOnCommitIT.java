package io.codehunters.messaging.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.messaging.OutboxMessage;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** A save requests a dispatch once its transaction commits, and never for a rollback. */
class DispatchOnCommitIT {

  private static final String ORIGIN = "sentinel-ms-test";

  private final AtomicInteger dispatchRequests = new AtomicInteger();
  private final AtomicInteger requestsBeforeCommit = new AtomicInteger(-1);
  private final AtomicInteger synchronizations = new AtomicInteger(-1);
  private OutboxJdbcAdapter adapter;
  private TransactionTemplate transaction;

  @BeforeEach
  void setUp() {
    DataSource dataSource = PostgresTestSupport.dataSource();
    PostgresTestSupport.truncate();
    var settings =
        new OutboxAdapterSettings(ORIGIN, Clock.systemUTC(), dispatchRequests::incrementAndGet);
    adapter = new OutboxJdbcAdapter(dataSource, JsonMapper.builder().build(), settings);
    transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
  }

  @Test
  void should_request_dispatch_after_commit_when_saved_in_committed_transaction() {
    transaction.executeWithoutResult(
        status -> {
          adapter.save(message("m1"));
          requestsBeforeCommit.set(dispatchRequests.get());
        });

    assertThat(requestsBeforeCommit).hasValue(0);
    assertThat(dispatchRequests).hasValue(1);
  }

  @Test
  void should_not_request_dispatch_when_transaction_rolls_back() {
    transaction.executeWithoutResult(
        status -> {
          adapter.save(message("m1"));
          status.setRollbackOnly();
        });

    assertThat(dispatchRequests).hasValue(0);
  }

  @Test
  void should_register_one_synchronization_when_several_saves_share_a_transaction() {
    transaction.executeWithoutResult(
        status -> {
          adapter.save(message("m1"));
          adapter.save(message("m2"));
          adapter.save(message("m3"));
          synchronizations.set(TransactionSynchronizationManager.getSynchronizations().size());
        });

    assertThat(synchronizations).hasValue(1);
    assertThat(dispatchRequests).hasValue(1);
  }

  @Test
  void should_request_dispatch_right_away_when_saved_without_transaction() {
    adapter.save(message("m1"));

    assertThat(dispatchRequests).hasValue(1);
  }

  private static OutboxMessage<String> message(String payload) {
    return OutboxMessage.<String>builder().topic("t").key("k").message(payload).build();
  }
}
