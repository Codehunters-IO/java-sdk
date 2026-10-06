package io.codehunters.audit.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.audit.AuditAutoConfiguration;
import io.codehunters.audit.AuditJdbcAutoConfiguration;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs audited {@code @Transactional} methods against Postgres, with Spring's default ordering of
 * the transaction advice and the audit aspect, and checks that the audit trail matches what was
 * committed.
 */
@Testcontainers
class AuditTransactionPostgresIT {

  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17-alpine");

  private static final String SUCCEEDED = "true:-";
  private static final String FAILED_BY_RULE = "false:java.lang.IllegalStateException";

  private static DataSource dataSource;

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(AuditJdbcAutoConfiguration.class, AuditAutoConfiguration.class))
          .withUserConfiguration(TransactionConfig.class)
          .withBean("dataSource", DataSource.class, () -> dataSource)
          .withBean(
              "transactionManager",
              PlatformTransactionManager.class,
              () -> new DataSourceTransactionManager(dataSource))
          .withBean(LedgerService.class, () -> new LedgerService(dataSource));

  @BeforeAll
  static void createTables() {
    dataSource = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    ReferenceDdlFixtures.load(dataSource, ReferenceDdlFixtures.POSTGRES_DDL);
    JdbcClient jdbc = JdbcClient.create(dataSource);
    jdbc.sql("CREATE TABLE ledger (id VARCHAR(20) PRIMARY KEY)").update();
    jdbc.sql(
            "CREATE TABLE deferred_ledger (id VARCHAR(20),"
                + " CONSTRAINT deferred_ledger_id UNIQUE (id) DEFERRABLE INITIALLY DEFERRED)")
        .update();
  }

  @BeforeEach
  void emptyTables() {
    JdbcClient.create(dataSource).sql("TRUNCATE audit_event, ledger, deferred_ledger").update();
  }

  @Test
  void should_commit_the_success_row_with_the_business_write_when_fail_on_error() {
    runner
        .withPropertyValues("codehunters.audit.fail-on-error=true")
        .run(ctx -> ctx.getBean(LedgerService.class).record("L-1"));

    assertThat(ledgerRows()).containsExactly("L-1");
    assertThat(auditOutcomes()).containsExactly(SUCCEEDED);
  }

  @Test
  void should_keep_the_error_row_when_the_method_throws_and_the_transaction_rolls_back() {
    runner
        .withPropertyValues("codehunters.audit.fail-on-error=true")
        .run(
            ctx ->
                assertThatThrownBy(() -> ctx.getBean(LedgerService.class).recordThenFail("L-2"))
                    .isInstanceOf(IllegalStateException.class));

    assertThat(ledgerRows()).isEmpty();
    assertThat(auditOutcomes()).containsExactly(FAILED_BY_RULE);
  }

  @Test
  void should_keep_the_error_row_when_a_sql_error_aborted_the_transaction() {
    runner
        .withPropertyValues("codehunters.audit.fail-on-error=true")
        .run(
            ctx ->
                assertThatThrownBy(
                        () -> ctx.getBean(LedgerService.class).recordThenBreakTheTransaction("L-3"))
                    .hasNoSuppressedExceptions());

    assertThat(auditOutcomes()).containsExactly("false:org.springframework.jdbc.BadSqlGrammarException");
  }

  @Test
  void should_roll_back_the_success_row_with_the_business_write_when_commit_fails() {
    runner
        .withPropertyValues("codehunters.audit.fail-on-error=true")
        .run(
            ctx ->
                assertThatThrownBy(
                    () -> ctx.getBean(LedgerService.class).recordTwiceFailingAtCommit("L-4")));

    assertThat(auditOutcomes()).isEmpty();
  }

  @Test
  void should_keep_the_error_row_when_publishing_asynchronously() {
    runner.run(
        ctx ->
            assertThatThrownBy(() -> ctx.getBean(LedgerService.class).recordThenFail("L-5"))
                .isInstanceOf(IllegalStateException.class));

    assertThat(auditOutcomes()).containsExactly(FAILED_BY_RULE);
  }

  @Test
  void should_record_rolled_back_not_success_when_commit_fails_and_publishing_asynchronously() {
    runner.run(
        ctx ->
            assertThatThrownBy(
                () -> ctx.getBean(LedgerService.class).recordTwiceFailingAtCommit("L-6")));

    assertThat(auditOutcomes()).containsExactly("false:ROLLED_BACK");
  }

  @Test
  void should_record_success_after_commit_when_publishing_synchronously_without_fail_on_error() {
    runner
        .withPropertyValues("codehunters.audit.async.enabled=false")
        .run(ctx -> ctx.getBean(LedgerService.class).record("L-7"));

    assertThat(auditOutcomes()).containsExactly(SUCCEEDED);
  }

  private static List<String> ledgerRows() {
    return JdbcClient.create(dataSource).sql("SELECT id FROM ledger").query(String.class).list();
  }

  private static List<String> auditOutcomes() {
    return JdbcClient.create(dataSource)
        .sql("SELECT success || ':' || COALESCE(error_message, '-') FROM audit_event")
        .query(String.class)
        .list();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  static class TransactionConfig {}
}
