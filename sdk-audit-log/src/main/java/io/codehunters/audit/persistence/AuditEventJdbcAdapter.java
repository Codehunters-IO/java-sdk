package io.codehunters.audit.persistence;

import io.codehunters.audit.AuditEvent;
import io.codehunters.audit.AuditEventPublisherPort;
import io.codehunters.audit.AuditPersistenceException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * JDBC-backed {@link AuditEventPublisherPort} that inserts audit events into the {@code
 * audit_event} table on the calling thread. Wrap it in {@link
 * io.codehunters.audit.AsyncAuditEventPublisher} to write off the caller's thread.
 *
 * <p>The insert takes part in the caller's Spring-managed transaction on the same data source, if
 * there is one: the row commits or rolls back with the business write. Without one it auto-commits.
 */
public class AuditEventJdbcAdapter implements AuditEventPublisherPort {

  private static final Logger log = LoggerFactory.getLogger(AuditEventJdbcAdapter.class);

  private static final String INSERT_SQL =
      """
      INSERT INTO audit_event (
        id, event_type, entity_id, action, user_id, role, resource, resource_id,
        http_method, http_status, ip_address, user_agent, request_body, response_summary,
        success, error_message, duration_ms, metadata, source_service, correlation_id,
        event_created_at
      ) VALUES (
        :id, :eventType, :entityId, :action, :userId, :role, :resource, :resourceId,
        :httpMethod, :httpStatus, :ipAddress, :userAgent, :requestBody, :responseSummary,
        :success, :errorMessage, :durationMs, :metadata, :sourceService, :correlationId,
        :eventCreatedAt
      )
      """;

  private final JdbcClient jdbcClient;
  private final JsonMapper auditJsonMapper;
  private final boolean failOnError;

  /**
   * Creates the adapter.
   *
   * @param dataSource the target data source
   * @param auditJsonMapper the JSON mapper for serializing payload maps
   * @param failOnError whether persistence failures should be rethrown
   */
  public AuditEventJdbcAdapter(
      DataSource dataSource, JsonMapper auditJsonMapper, boolean failOnError) {
    this.jdbcClient = JdbcClient.create(dataSource);
    this.auditJsonMapper = auditJsonMapper;
    this.failOnError = failOnError;
  }

  @Override
  public void publish(AuditEvent event) {
    log.debug("[START] persist audit event: {} {}", event.action(), event.entityType());
    try {
      var id = UUID.randomUUID();
      jdbcClient
          .sql(INSERT_SQL)
          .param("id", id)
          .param("eventType", event.entityType())
          .param("entityId", event.entityId())
          .param("action", event.action())
          .param("userId", event.actorId())
          .param("role", event.actorType())
          .param("resource", event.resource())
          .param("resourceId", event.resourceId())
          .param("httpMethod", event.httpMethod())
          .param("httpStatus", event.httpStatus())
          .param("ipAddress", event.ipAddress())
          .param("userAgent", event.userAgent())
          .param("requestBody", serializeMap(event.payload()))
          .param("responseSummary", serializeMap(event.responseSummary()))
          .param("success", event.success())
          .param("errorMessage", event.errorMessage())
          .param("durationMs", event.durationMs())
          .param("metadata", serializeMap(event.metadata()))
          .param("sourceService", event.sourceService())
          .param("correlationId", event.correlationId())
          .param(
              "eventCreatedAt",
              event.createdAt() != null ? Timestamp.from(event.createdAt().toInstant()) : null)
          .update();
      log.debug("[END] persist audit event: {}", id);
    } catch (Exception e) {
      if (failOnError) {
        throw new AuditPersistenceException("Failed to persist audit event", e);
      }
      log.error("Failed to persist audit event: {}", e.getMessage(), e);
    }
  }

  private String serializeMap(Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return null;
    }
    try {
      return auditJsonMapper.writeValueAsString(map);
    } catch (Exception e) {
      log.warn("Failed to serialize audit map: {}", e.getMessage());
      return map.toString();
    }
  }
}
