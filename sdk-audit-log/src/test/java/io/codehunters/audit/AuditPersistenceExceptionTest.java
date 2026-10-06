package io.codehunters.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class AuditPersistenceExceptionTest {

  private static final String ROW_VALUE = "alice@example.com";
  private static final String SQL_STATE = "23502";
  private static final int VENDOR_CODE = 515;

  private final DataIntegrityViolationException failure =
      new DataIntegrityViolationException(
          "INSERT failed for " + ROW_VALUE,
          new SQLException("Failing row contains (" + ROW_VALUE + ")", SQL_STATE, VENDOR_CODE));

  @Test
  void should_not_carry_row_values_anywhere_in_its_stack_trace_when_redacted() {
    AuditPersistenceException redacted =
        AuditPersistenceException.redacted("Failed to persist audit event", failure);

    assertThat(stackTraceOf(redacted)).doesNotContain(ROW_VALUE);
  }

  @Test
  void should_expose_the_sql_state_when_redacted() {
    AuditPersistenceException redacted =
        AuditPersistenceException.redacted("Failed to persist audit event", failure);

    assertThat(redacted.sqlState()).contains(SQL_STATE);
  }

  @Test
  void should_expose_the_vendor_code_when_redacted() {
    AuditPersistenceException redacted =
        AuditPersistenceException.redacted("Failed to persist audit event", failure);

    assertThat(redacted.vendorCode()).contains(VENDOR_CODE);
  }

  @Test
  void should_name_the_failure_class_when_redacted() {
    AuditPersistenceException redacted =
        AuditPersistenceException.redacted("Failed to persist audit event", failure);

    assertThat(redacted.failureClass()).isEqualTo(DataIntegrityViolationException.class.getName());
    assertThat(redacted).hasMessageContaining(DataIntegrityViolationException.class.getName());
  }

  @Test
  void should_have_no_sql_state_when_the_failure_is_not_a_database_error() {
    AuditPersistenceException redacted =
        AuditPersistenceException.redacted(
            "Failed to build audit event", new IllegalStateException());

    assertThat(redacted.sqlState()).isEmpty();
    assertThat(redacted.vendorCode()).isEmpty();
  }

  @Test
  void should_keep_the_given_cause_when_built_with_the_public_constructor() {
    var cause = new IllegalStateException("boom");

    var exception = new AuditPersistenceException("insert failed", cause);

    assertThat(exception).hasCause(cause);
    assertThat(exception.failureClass()).isEqualTo(IllegalStateException.class.getName());
  }

  private static String stackTraceOf(Throwable throwable) {
    var out = new StringWriter();
    throwable.printStackTrace(new PrintWriter(out));
    return out.toString();
  }
}
