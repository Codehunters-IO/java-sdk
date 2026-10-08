package io.codehunters.audit.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.audit.AuditEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * With a JTA transaction manager that has a JTA {@code TransactionManager}, an isolated audit write
 * suspends the caller's global transaction, so it commits even when the caller rolls back.
 */
class RequiresNewJtaTest {

  private final RecordingJta jta = new RecordingJta();
  private final JtaTransactionManager manager = new JtaTransactionManager(jta, jta);
  private final List<Boolean> writtenInATransaction = new CopyOnWriteArrayList<>();
  private final RequiresNewAuditEventPublisher publisher =
      new RequiresNewAuditEventPublisher(this::write, manager);

  @Test
  void should_write_in_its_own_transaction_after_suspending_the_callers() {
    new TransactionTemplate(manager)
        .executeWithoutResult(status -> publisher.publish(H2AuditDatabase.SUCCESS));

    assertThat(jta.calls())
        .containsExactly("begin", "suspend", "begin", "commit", "resume", "commit");
    assertThat(writtenInATransaction).containsExactly(true);
  }

  @Test
  void should_commit_the_audit_row_when_the_callers_transaction_rolls_back() {
    new TransactionTemplate(manager)
        .executeWithoutResult(
            status -> {
              publisher.publish(H2AuditDatabase.SUCCESS);
              status.setRollbackOnly();
            });

    assertThat(jta.calls())
        .containsExactly("begin", "suspend", "begin", "commit", "resume", "rollback");
  }

  private void write(AuditEvent event) {
    writtenInATransaction.add(jta.isActive());
  }
}
