package io.codehunters.audit.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.audit.AuditEvent;
import io.codehunters.messaging.factory.EventMessageFactory;
import io.codehunters.messaging.factory.OutboxEventFactory;
import io.codehunters.messaging.factory.TraceIdSource;
import io.codehunters.messaging.jdbc.OutboxJdbcAdapter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes audit events through the real JDBC outbox on PostgreSQL and checks that they commit or
 * roll back with the caller's transaction.
 */
@Testcontainers
class AuditOutboxPostgresIT {

  @Container static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17-alpine");

  private static final String OUTBOX_DDL =
      "db/migration/outbox/postgresql/outbox_message_create.sql";
  private static final String TOPIC = "audit.test";
  private static final String ENTITY_ID = "DOC-7";
  private static final String ACTION = "DOCUMENT_CLASSIFIED";

  private static DataSource dataSource;

  private TransactionTemplate transaction;
  private OutboxAuditEventPublisher publisher;

  @BeforeAll
  static void createTable() {
    dataSource = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    new ResourceDatabasePopulator(new ClassPathResource(OUTBOX_DDL)).execute(dataSource);
  }

  @BeforeEach
  void emptyTable() {
    JdbcClient.create(dataSource).sql("TRUNCATE outbox_message").update();
    DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
    transaction = new TransactionTemplate(transactionManager);
    publisher =
        new OutboxAuditEventPublisher(
            new OutboxJdbcAdapter(dataSource, JsonMapper.builder().build(), "audit-test"),
            new OutboxEventFactory(
                new EventMessageFactory("audit-test", TraceIdSource.none(), Clock.systemUTC())),
            new OutboxAuditSettings(TOPIC, transactionManager));
  }

  @Test
  void should_leave_no_row_when_caller_transaction_rolls_back() {
    transaction.executeWithoutResult(
        status -> {
          publisher.publish(event());
          status.setRollbackOnly();
        });

    assertThat(rowCount()).isZero();
  }

  @Test
  void should_commit_one_row_with_key_topic_and_envelope_when_caller_commits() {
    transaction.executeWithoutResult(status -> publisher.publish(event()));

    assertThat(rows()).containsExactly(TOPIC + "|" + ENTITY_ID);
    assertThat(payload()).contains("\"eventName\":\"" + ACTION + "\"").contains(ENTITY_ID);
  }

  @Test
  void should_commit_detached_row_when_caller_transaction_rolls_back() {
    transaction.executeWithoutResult(
        status -> {
          publisher.publishDetached(event());
          status.setRollbackOnly();
        });

    assertThat(rowCount()).isOne();
  }

  private static AuditEvent event() {
    return new AuditEvent(
        "DOCUMENT",
        ENTITY_ID,
        ACTION,
        "user-1",
        "USER",
        "10.0.0.1",
        Map.of("pages", 3),
        "SUCCESS",
        null,
        "sentinel-ms-ocr",
        OffsetDateTime.parse("2026-10-11T10:00:00Z"));
  }

  private static long rowCount() {
    return JdbcClient.create(dataSource)
        .sql("SELECT COUNT(*) FROM outbox_message")
        .query(Long.class)
        .single();
  }

  private static List<String> rows() {
    return JdbcClient.create(dataSource)
        .sql("SELECT topic || '|' || message_key FROM outbox_message")
        .query(String.class)
        .list();
  }

  private static String payload() {
    return JdbcClient.create(dataSource)
        .sql("SELECT payload FROM outbox_message")
        .query(String.class)
        .single();
  }
}
