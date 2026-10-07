package io.codehunters.audit.persistence;

import static io.codehunters.audit.persistence.H2AuditDatabase.ERROR;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Writes that must commit even though the caller's transaction rolls back. */
class IsolatedAuditWritesTest {

  private static final String H2_USER = "";
  private static final String H2_PASSWORD = "";

  private final DataSource dataSource = H2AuditDatabase.create();
  private final DataSourceTransactionManager transactionManager =
      new DataSourceTransactionManager(dataSource);
  private final TransactionTemplate transaction = new TransactionTemplate(transactionManager);
  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  @Test
  void should_roll_back_a_joined_write_with_the_callers_transaction() {
    var joined = new AuditEventJdbcAdapter(dataSource, jsonMapper, true);

    transaction.executeWithoutResult(
        status -> {
          joined.publish(ERROR);
          status.setRollbackOnly();
        });

    assertThat(H2AuditDatabase.countRows(dataSource)).isZero();
  }

  @Test
  void should_keep_a_requires_new_write_when_the_callers_transaction_rolls_back() {
    var isolated =
        new RequiresNewAuditEventPublisher(
            new AuditEventJdbcAdapter(dataSource, jsonMapper, true), transactionManager);

    transaction.executeWithoutResult(
        status -> {
          isolated.publish(ERROR);
          status.setRollbackOnly();
        });

    assertThat(H2AuditDatabase.countRows(dataSource)).isEqualTo(1);
  }

  @Test
  void should_keep_an_own_connection_write_when_the_callers_transaction_rolls_back() {
    var isolated =
        new AuditEventJdbcAdapter(new AutoCommitDataSource(dataSource), jsonMapper, true);

    transaction.executeWithoutResult(
        status -> {
          isolated.publish(ERROR);
          status.setRollbackOnly();
        });

    assertThat(H2AuditDatabase.countRows(dataSource)).isEqualTo(1);
  }

  @Test
  void should_commit_an_own_connection_write_when_the_pool_hands_out_manual_commit_connections() {
    var isolated =
        new AuditEventJdbcAdapter(
            new AutoCommitDataSource(new ManualCommitDataSource(dataSource)), jsonMapper, true);

    isolated.publish(ERROR);

    assertThat(H2AuditDatabase.countRows(dataSource)).isEqualTo(1);
  }

  @Test
  void should_hand_out_auto_commit_connections_when_asked_with_explicit_credentials()
      throws SQLException {
    var view = new AutoCommitDataSource(new ManualCommitDataSource(dataSource));

    try (Connection connection = view.getConnection(H2_USER, H2_PASSWORD)) {
      assertThat(connection.getAutoCommit()).isTrue();
    }
  }

  /** A pool configured with {@code auto-commit=false}. */
  private static final class ManualCommitDataSource extends DelegatingDataSource {

    ManualCommitDataSource(DataSource target) {
      super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
      Connection connection = super.getConnection();
      connection.setAutoCommit(false);
      return connection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      Connection connection = super.getConnection(username, password);
      connection.setAutoCommit(false);
      return connection;
    }
  }
}
