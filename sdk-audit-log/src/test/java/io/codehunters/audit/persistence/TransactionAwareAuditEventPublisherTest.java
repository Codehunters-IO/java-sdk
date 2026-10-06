package io.codehunters.audit.persistence;

import static io.codehunters.audit.persistence.H2AuditDatabase.ERROR;
import static io.codehunters.audit.persistence.H2AuditDatabase.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.audit.AuditEvent;
import io.codehunters.audit.AuditEventPublisherPort;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class TransactionAwareAuditEventPublisherTest {

  private final TransactionTemplate transaction =
      new TransactionTemplate(new DataSourceTransactionManager(H2AuditDatabase.create()));
  private final List<AuditEvent> joined = new ArrayList<>();
  private final List<AuditEvent> outside = new ArrayList<>();
  private final AuditEventPublisherPort atomic =
      TransactionAwareAuditEventPublisher.atomic(joined::add, outside::add);
  private final AuditEventPublisherPort deferred =
      TransactionAwareAuditEventPublisher.deferred(outside::add);

  @AfterEach
  void clearTransactionState() {
    TransactionSynchronizationManager.clear();
  }

  @Test
  void should_publish_outside_when_no_transaction_is_active() {
    atomic.publish(SUCCESS);

    assertThat(outside).containsExactly(SUCCESS);
    assertThat(joined).isEmpty();
  }

  @Test
  void should_write_success_inside_the_transaction_when_atomic() {
    transaction.executeWithoutResult(status -> atomic.publish(SUCCESS));

    assertThat(joined).containsExactly(SUCCESS);
    assertThat(outside).isEmpty();
  }

  @Test
  void should_publish_error_outside_the_transaction_before_it_completes() {
    List<AuditEvent> seenInside =
        transaction.execute(
            status -> {
              atomic.publish(ERROR);
              return List.copyOf(outside);
            });

    assertThat(seenInside).containsExactly(ERROR);
    assertThat(joined).isEmpty();
  }

  @Test
  void should_defer_success_until_the_transaction_commits() {
    List<AuditEvent> seenInside =
        transaction.execute(
            status -> {
              deferred.publish(SUCCESS);
              return List.copyOf(outside);
            });

    assertThat(seenInside).isEmpty();
    assertThat(outside).containsExactly(SUCCESS);
  }

  @Test
  void should_record_rolled_back_when_the_transaction_rolls_back() {
    transaction.executeWithoutResult(
        status -> {
          deferred.publish(SUCCESS);
          status.setRollbackOnly();
        });

    assertThat(outside.getFirst().status()).isEqualTo("ROLLED_BACK");
    assertThat(outside.getFirst().success()).isFalse();
    assertThat(outside.getFirst().errorMessage()).isEqualTo("ROLLED_BACK");
  }

  @Test
  void should_record_unknown_when_the_transaction_outcome_is_unknown() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    deferred.publish(SUCCESS);

    TransactionSynchronizationManager.getSynchronizations()
        .getFirst()
        .afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);

    assertThat(outside.getFirst().status()).isEqualTo("UNKNOWN");
    assertThat(outside.getFirst().success()).isNull();
    assertThat(outside.getFirst().errorMessage()).isEqualTo("UNKNOWN");
  }

  @Test
  void should_publish_at_once_when_the_transaction_has_no_synchronization() {
    TransactionSynchronizationManager.setActualTransactionActive(true);

    deferred.publish(SUCCESS);

    assertThat(outside).containsExactly(SUCCESS);
  }
}
