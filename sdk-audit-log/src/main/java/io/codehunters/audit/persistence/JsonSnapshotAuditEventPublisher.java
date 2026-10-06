package io.codehunters.audit.persistence;

import io.codehunters.audit.AuditEvent;
import io.codehunters.audit.AuditEventPublisherPort;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Replaces the payload, response summary and metadata maps of an {@link AuditEvent} with JSON
 * copies before handing it on. Runs on the caller's thread, so an event written later, after the
 * transaction commits or on a worker thread, records the values as they were when the method
 * returned: later changes to the arguments and closed persistence sessions cannot reach it.
 *
 * <p>A map that cannot be serialized is replaced by {@code null} and a warning naming the field and
 * the exception class is logged; its {@code toString()} is never stored.
 *
 * @since 3.0.0
 */
public class JsonSnapshotAuditEventPublisher implements AuditEventPublisherPort {

  private static final Logger log = LoggerFactory.getLogger(JsonSnapshotAuditEventPublisher.class);

  private final AuditEventPublisherPort delegate;
  private final JsonMapper jsonMapper;

  /**
   * Creates the publisher.
   *
   * @param delegate the publisher that receives the copied event
   * @param jsonMapper the mapper that copies the maps
   */
  public JsonSnapshotAuditEventPublisher(AuditEventPublisherPort delegate, JsonMapper jsonMapper) {
    this.delegate = delegate;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public void publish(AuditEvent event) {
    delegate.publish(
        new AuditEvent(
            event.entityType(),
            event.entityId(),
            event.action(),
            event.actorId(),
            event.actorType(),
            event.ipAddress(),
            snapshot("payload", event.payload()),
            event.status(),
            event.correlationId(),
            event.sourceService(),
            event.createdAt(),
            event.resource(),
            event.resourceId(),
            event.httpMethod(),
            event.httpStatus(),
            event.userAgent(),
            snapshot("responseSummary", event.responseSummary()),
            event.success(),
            event.errorMessage(),
            event.durationMs(),
            snapshot("metadata", event.metadata())));
  }

  private Map<String, Object> snapshot(String field, Map<String, Object> values) {
    if (values == null || values.isEmpty()) {
      return values;
    }
    try {
      return copyOf(jsonMapper.valueToTree(values));
    } catch (RuntimeException ex) {
      log.warn(
          "Audit {} could not be serialized and is dropped: {}", field, ex.getClass().getName());
      return null;
    }
  }

  private static Map<String, Object> copyOf(JsonNode tree) {
    Map<String, Object> copy = new LinkedHashMap<>();
    tree.properties().forEach(property -> copy.put(property.getKey(), property.getValue()));
    return Collections.unmodifiableMap(copy);
  }
}
