package io.codehunters.audit.outbox;

import io.codehunters.audit.AuditEvent;
import io.codehunters.audit.AuditEventPublisherPort;
import io.codehunters.messaging.EventMessage;
import io.codehunters.messaging.OutboxEvent;
import io.codehunters.messaging.OutboxMessage;
import io.codehunters.messaging.OutboxWriter;
import io.codehunters.messaging.factory.OutboxEventFactory;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes each audit event through the transactional outbox, synchronously and in the caller's
 * transaction: the event commits or rolls back with the business write, so the audit trail records
 * what committed. An event of a failed call ({@code success=false}) joins the transaction too;
 * nothing is written asynchronously or in a new transaction.
 *
 * <p>The wire payload is the sanitized {@link AuditEvent} itself inside the {@link EventMessage}
 * envelope: the event name is the action ({@value #FALLBACK_TYPE} when blank) and the message key
 * is the entity id. An event without entity id is written as an unordered, keyless row, which never
 * blocks the rows of another key. Sanitizing drops the IP address, the user agent and the response
 * summary, and keeps the error message only when it is a Java class name, {@code ROLLED_BACK} or
 * {@code UNKNOWN}.
 *
 * <p>Failures of the outbox writer propagate unchanged.
 *
 * @since 5.1.0
 */
public class OutboxAuditEventPublisher implements AuditEventPublisherPort {

  /** The event name used when the audit event has no action. */
  public static final String FALLBACK_TYPE = "AUDIT";

  // The staged builder requires a key; keyless rows are built with this one, then cleared.
  private static final String KEYLESS_PLACEHOLDER = "unkeyed";
  private static final Pattern CLASS_NAME =
      Pattern.compile("^[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*$");
  private static final Set<String> OUTCOME_MARKERS = Set.of("ROLLED_BACK", "UNKNOWN");

  private final OutboxWriter writer;
  private final OutboxEventFactory factory;
  private final String topic;
  private final TransactionTemplate detached;

  /**
   * Creates the publisher.
   *
   * @param writer the outbox writer, which joins the caller's transaction
   * @param factory the factory of the outbox message and its envelope
   * @param settings the topic and the transaction manager of detached events
   */
  public OutboxAuditEventPublisher(
      OutboxWriter writer, OutboxEventFactory factory, OutboxAuditSettings settings) {
    this.writer = writer;
    this.factory = factory;
    this.topic = settings.topic();
    this.detached = notSupported(settings);
  }

  @Override
  public void publish(AuditEvent event) {
    AuditEvent sanitized = sanitize(event);
    OutboxMessage<EventMessage<AuditEvent>> message =
        factory.create(
            OutboxEvent.to(topic).key(keyOf(event)).type(typeOf(event)).payload(sanitized));
    writer.save(hasText(event.entityId()) ? message : message.toBuilder().key(null).build());
  }

  /**
   * Writes the event outside any transaction bound to the current thread, which belongs to someone
   * else: the transaction is suspended when a transaction manager is configured, otherwise the
   * event is written directly.
   *
   * @param event the audit event to publish
   */
  @Override
  public void publishDetached(AuditEvent event) {
    if (detached == null) {
      publish(event);
      return;
    }
    detached.executeWithoutResult(status -> publish(event));
  }

  private static TransactionTemplate notSupported(OutboxAuditSettings settings) {
    if (settings.detachedTransactionManager() == null) {
      return null;
    }
    TransactionTemplate template = new TransactionTemplate(settings.detachedTransactionManager());
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    return template;
  }

  private static String keyOf(AuditEvent event) {
    return hasText(event.entityId()) ? event.entityId() : KEYLESS_PLACEHOLDER;
  }

  private static String typeOf(AuditEvent event) {
    return hasText(event.action()) ? event.action() : FALLBACK_TYPE;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static String safeErrorMessage(String errorMessage) {
    if (errorMessage == null) {
      return null;
    }
    boolean safe =
        OUTCOME_MARKERS.contains(errorMessage) || CLASS_NAME.matcher(errorMessage).matches();
    return safe ? errorMessage : null;
  }

  private static AuditEvent sanitize(AuditEvent event) {
    return new AuditEvent(
        event.entityType(),
        event.entityId(),
        event.action(),
        event.actorId(),
        event.actorType(),
        null,
        event.payload(),
        event.status(),
        event.correlationId(),
        event.sourceService(),
        event.createdAt(),
        event.resource(),
        event.resourceId(),
        event.httpMethod(),
        event.httpStatus(),
        null,
        null,
        event.success(),
        safeErrorMessage(event.errorMessage()),
        event.durationMs(),
        event.metadata());
  }
}
