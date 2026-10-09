package io.codehunters.audit;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable audit event produced by {@link io.codehunters.audit.aspect.AuditAspect}.
 *
 * @param entityType the type of entity audited
 * @param entityId the identifier of the audited entity
 * @param action the action performed
 * @param actorId the identifier of the actor
 * @param actorType the type of actor
 * @param ipAddress the caller IP address
 * @param payload the audited payload
 * @param status the outcome status
 * @param correlationId the correlation identifier
 * @param sourceService the originating service
 * @param createdAt the creation timestamp
 * @param resource the requested resource
 * @param resourceId the requested resource identifier
 * @param httpMethod the HTTP method
 * @param httpStatus the HTTP status code
 * @param userAgent the caller user agent
 * @param responseSummary a summary of the response
 * @param success whether the action succeeded
 * @param errorMessage the class name of the exception when the action failed, never its message; or
 *     {@code ROLLED_BACK} / {@code UNKNOWN} when the action returned but its transaction did not
 *     commit
 * @param durationMs the execution duration in milliseconds
 * @param metadata arbitrary additional metadata
 * @since 1.0.0
 */
public record AuditEvent(
    String entityType,
    String entityId,
    String action,
    String actorId,
    String actorType,
    String ipAddress,
    Map<String, Object> payload,
    String status,
    UUID correlationId,
    String sourceService,
    OffsetDateTime createdAt,
    String resource,
    String resourceId,
    String httpMethod,
    Integer httpStatus,
    String userAgent,
    Map<String, Object> responseSummary,
    Boolean success,
    String errorMessage,
    Integer durationMs,
    Map<String, Object> metadata) {

  /**
   * Backwards-compatible constructor for existing callers; extended fields default to {@code null}.
   *
   * @param entityType the type of entity audited
   * @param entityId the identifier of the audited entity
   * @param action the action performed
   * @param actorId the identifier of the actor
   * @param actorType the type of actor
   * @param ipAddress the caller IP address
   * @param payload the audited payload
   * @param status the outcome status
   * @param correlationId the correlation identifier
   * @param sourceService the originating service
   * @param createdAt the creation timestamp
   */
  public AuditEvent(
      String entityType,
      String entityId,
      String action,
      String actorId,
      String actorType,
      String ipAddress,
      Map<String, Object> payload,
      String status,
      UUID correlationId,
      String sourceService,
      OffsetDateTime createdAt) {
    this(
        entityType,
        entityId,
        action,
        actorId,
        actorType,
        ipAddress,
        payload,
        status,
        correlationId,
        sourceService,
        createdAt,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  /**
   * Returns a copy of this event with a different outcome.
   *
   * @param status the new status
   * @param success whether the action succeeded, or {@code null} if unknown
   * @param errorMessage the new error description
   * @return the copied event
   * @since 3.0.0
   */
  public AuditEvent withOutcome(String status, Boolean success, String errorMessage) {
    return new AuditEvent(
        entityType,
        entityId,
        action,
        actorId,
        actorType,
        ipAddress,
        payload,
        status,
        correlationId,
        sourceService,
        createdAt,
        resource,
        resourceId,
        httpMethod,
        httpStatus,
        userAgent,
        responseSummary,
        success,
        errorMessage,
        durationMs,
        metadata);
  }
}
