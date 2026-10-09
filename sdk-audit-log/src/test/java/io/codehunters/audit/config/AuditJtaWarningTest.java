package io.codehunters.audit.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.audit.AuditEventPublisherPort;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.jta.JtaTransactionManager;

/** Warns when failure events would be enlisted in a JTA transaction and roll back with it. */
@ExtendWith(OutputCaptureExtension.class)
class AuditJtaWarningTest {

  private static final String WARNING = "JTA transaction manager";

  private final DataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:jta");

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(AuditJdbcAutoConfiguration.class, AuditAutoConfiguration.class))
          .withBean("dataSource", DataSource.class, () -> dataSource);

  @Test
  void should_warn_when_a_jta_manager_exists_but_none_is_unique(CapturedOutput output) {
    runner
        .withBean("jta", PlatformTransactionManager.class, AuditJtaWarningTest::jtaManager)
        .withBean("jdbc", PlatformTransactionManager.class, this::jdbcManager)
        .run(ctx -> assertThat(ctx).hasSingleBean(AuditEventPublisherPort.class));

    assertThat(output).contains(WARNING);
  }

  @Test
  void should_warn_when_the_only_jta_manager_cannot_suspend(CapturedOutput output) {
    runner
        .withBean("jta", PlatformTransactionManager.class, AuditJtaWarningTest::jtaManager)
        .run(ctx -> assertThat(ctx).hasSingleBean(AuditEventPublisherPort.class));

    assertThat(output).contains(WARNING);
  }

  @Test
  void should_not_warn_when_the_only_jta_manager_can_suspend(CapturedOutput output) {
    runner
        .withBean("jta", PlatformTransactionManager.class, AuditJtaWarningTest::suspendingJta)
        .run(ctx -> assertThat(ctx).hasSingleBean(AuditEventPublisherPort.class));

    assertThat(output).doesNotContain(WARNING);
  }

  @Test
  void should_not_warn_when_several_managers_exist_and_none_is_jta(CapturedOutput output) {
    runner
        .withBean("first", PlatformTransactionManager.class, this::jdbcManager)
        .withBean("second", PlatformTransactionManager.class, this::jdbcManager)
        .run(ctx -> assertThat(ctx).hasSingleBean(AuditEventPublisherPort.class));

    assertThat(output).doesNotContain(WARNING);
  }

  private PlatformTransactionManager jdbcManager() {
    return new DataSourceTransactionManager(dataSource);
  }

  private static PlatformTransactionManager jtaManager() {
    return new JtaTransactionManager(new NoOpUserTransaction());
  }

  private static PlatformTransactionManager suspendingJta() {
    return new JtaTransactionManager(new NoOpUserTransaction(), new NoOpTransactionManager());
  }

  /** Never called: the tests only start the context. */
  private static final class NoOpUserTransaction implements UserTransaction {

    @Override
    public void begin() {}

    @Override
    public void commit() {}

    @Override
    public void rollback() {}

    @Override
    public void setRollbackOnly() {}

    @Override
    public int getStatus() {
      return 0;
    }

    @Override
    public void setTransactionTimeout(int seconds) {}
  }

  /** Never called: the tests only start the context. */
  private static final class NoOpTransactionManager implements TransactionManager {

    @Override
    public void begin() {}

    @Override
    public void commit() {}

    @Override
    public int getStatus() {
      return 0;
    }

    @Override
    public Transaction getTransaction() {
      return null;
    }

    @Override
    public void resume(Transaction transaction) {}

    @Override
    public void rollback() {}

    @Override
    public void setRollbackOnly() {}

    @Override
    public void setTransactionTimeout(int seconds) {}

    @Override
    public Transaction suspend() {
      return null;
    }
  }
}
