package io.codehunters.audit.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.audit.AuditEvent;
import io.codehunters.messaging.EventMessage;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.OutboxWriter;
import io.codehunters.messaging.factory.EventMessageFactory;
import io.codehunters.messaging.factory.OutboxEventFactory;
import io.codehunters.messaging.factory.TraceIdSource;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxAuditEventPublisherTest {

  private static final String TOPIC = "audit.test";
  private static final String ENTITY_ID = "DOC-42";
  private static final String ACTION = "DOCUMENT_UPLOADED";
  private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");

  private final Recording writer = new Recording();
  private final OutboxEventFactory factory =
      new OutboxEventFactory(
          new EventMessageFactory(
              "audit-test", TraceIdSource.none(), Clock.fixed(NOW, ZoneOffset.UTC)));
  private final NoOpTransactionManager transactionManager = new NoOpTransactionManager();
  private final OutboxAuditEventPublisher publisher =
      new OutboxAuditEventPublisher(
          writer, factory, new OutboxAuditSettings(TOPIC, transactionManager));

  @Test
  void should_write_one_outbox_message_keyed_by_entity_id_when_publishing() {
    publisher.publish(event(ENTITY_ID, ACTION, null));

    assertThat(writer.messages).hasSize(1);
    assertThat(writer.messages.getFirst().topic()).isEqualTo(TOPIC);
    assertThat(writer.messages.getFirst().key()).isEqualTo(ENTITY_ID);
    assertThat(body().eventName()).isEqualTo(ACTION);
    assertThat(body().payload()).isInstanceOf(AuditEvent.class);
  }

  @Test
  void should_drop_network_fields_when_sanitizing() {
    publisher.publish(event(ENTITY_ID, ACTION, null));

    assertThat(written())
        .extracting(AuditEvent::ipAddress, AuditEvent::userAgent, AuditEvent::responseSummary)
        .containsOnlyNulls();
  }

  @Test
  void should_keep_identifying_fields_when_sanitizing() {
    AuditEvent event = event(ENTITY_ID, ACTION, null);

    publisher.publish(event);

    assertThat(written())
        .usingRecursiveComparison()
        .ignoringFields("ipAddress", "userAgent", "responseSummary")
        .isEqualTo(event);
  }

  @Test
  void should_keep_error_message_when_it_is_a_class_name() {
    publisher.publish(event(ENTITY_ID, ACTION, "java.lang.IllegalStateException"));

    assertThat(written().errorMessage()).isEqualTo("java.lang.IllegalStateException");
  }

  @Test
  void should_keep_error_message_when_it_is_rolled_back() {
    publisher.publish(event(ENTITY_ID, ACTION, "ROLLED_BACK"));

    assertThat(written().errorMessage()).isEqualTo("ROLLED_BACK");
  }

  @Test
  void should_keep_error_message_when_it_is_unknown() {
    publisher.publish(event(ENTITY_ID, ACTION, "UNKNOWN"));

    assertThat(written().errorMessage()).isEqualTo("UNKNOWN");
  }

  @Test
  void should_drop_error_message_when_it_is_free_text() {
    publisher.publish(event(ENTITY_ID, ACTION, "card 4111 declined"));

    assertThat(written().errorMessage()).isNull();
  }

  @Test
  void should_write_keyless_row_when_entity_id_is_null() {
    publisher.publish(event(null, ACTION, null));

    assertThat(writer.messages.getFirst().key()).isNull();
  }

  @Test
  void should_write_keyless_row_when_entity_id_is_blank() {
    publisher.publish(event("  ", ACTION, null));

    assertThat(writer.messages.getFirst().key()).isNull();
  }

  @Test
  void should_use_audit_type_when_action_is_blank() {
    publisher.publish(event(ENTITY_ID, " ", null));

    assertThat(body().eventName()).isEqualTo("AUDIT");
  }

  @Test
  void should_use_audit_type_when_action_is_null() {
    publisher.publish(event(ENTITY_ID, null, null));

    assertThat(body().eventName()).isEqualTo("AUDIT");
  }

  @Test
  void should_join_caller_transaction_when_success_is_false() {
    AuditEvent failed = event(ENTITY_ID, ACTION, null).withOutcome("FAILURE", false, null);

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(status -> publisher.publish(failed));

    assertThat(writer.inTransaction).containsExactly(true);
  }

  @Test
  void should_write_outside_caller_transaction_when_publishing_detached() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(status -> publisher.publishDetached(event(ENTITY_ID, ACTION, null)));

    assertThat(writer.inTransaction).containsExactly(false);
  }

  @Test
  void should_write_directly_when_publishing_detached_without_transaction_manager() {
    OutboxAuditEventPublisher direct =
        new OutboxAuditEventPublisher(writer, factory, new OutboxAuditSettings(TOPIC, null));

    direct.publishDetached(event(ENTITY_ID, ACTION, null));

    assertThat(writer.messages).hasSize(1);
  }

  @Test
  void should_propagate_writer_failure_when_saving_fails() {
    OutboxAuditEventPublisher failing =
        new OutboxAuditEventPublisher(new Failing(), factory, new OutboxAuditSettings(TOPIC, null));

    assertThatThrownBy(() -> failing.publish(event(ENTITY_ID, ACTION, null)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void should_reject_settings_when_topic_is_blank() {
    assertThatThrownBy(() -> new OutboxAuditSettings(" ", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_settings_when_topic_is_null() {
    assertThatThrownBy(() -> new OutboxAuditSettings(null, null))
        .isInstanceOf(NullPointerException.class);
  }

  private EventMessage.Body<?> body() {
    return ((EventMessage<?>) writer.messages.getFirst().message()).body();
  }

  private AuditEvent written() {
    return (AuditEvent) body().payload();
  }

  private static AuditEvent event(String entityId, String action, String errorMessage) {
    return new AuditEvent(
        "DOCUMENT",
        entityId,
        action,
        "user-1",
        "USER",
        "10.0.0.1",
        Map.of("pages", 3),
        "SUCCESS",
        UUID.fromString("4bf92f35-77b3-4da6-a3ce-929d0e0e4736"),
        "sentinel-ms-ocr",
        OffsetDateTime.parse("2026-10-11T10:00:00Z"),
        "/api/documents",
        "res-1",
        "POST",
        201,
        "Mozilla/5.0",
        Map.of("status", "ok"),
        true,
        errorMessage,
        12,
        Map.of("tenant", "t-1"));
  }

  private static final class Recording implements OutboxWriter {
    private final List<OutboxMessage<?>> messages = new ArrayList<>();
    private final List<Boolean> inTransaction = new ArrayList<>();

    @Override
    public <T> void save(OutboxMessage<T> outboxMessage) {
      messages.add(outboxMessage);
      inTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
    }
  }

  private static final class Failing implements OutboxWriter {
    @Override
    public <T> void save(OutboxMessage<T> outboxMessage) {
      throw new IllegalStateException("outbox unavailable");
    }
  }

  // Binds a marker resource like a real manager, so a nested NOT_SUPPORTED template suspends it.
  private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {
    @Override
    protected Object doGetTransaction() {
      return this;
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
      return TransactionSynchronizationManager.hasResource(this);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      TransactionSynchronizationManager.bindResource(this, Boolean.TRUE);
    }

    @Override
    protected Object doSuspend(Object transaction) {
      return TransactionSynchronizationManager.unbindResource(this);
    }

    @Override
    protected void doResume(Object transaction, Object suspendedResources) {
      TransactionSynchronizationManager.bindResource(this, suspendedResources);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
      TransactionSynchronizationManager.unbindResourceIfPossible(this);
    }
  }
}
