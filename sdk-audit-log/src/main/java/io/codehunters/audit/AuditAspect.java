package io.codehunters.audit;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;

/**
 * Intercepts methods annotated with {@link Auditable} and publishes an {@link AuditEvent} via the
 * configured {@link AuditEventPublisherPort}.
 *
 * @since 1.0.0
 */
@Aspect
public class AuditAspect {

  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final String STATUS_ERROR = "ERROR";

  private final Logger log = LoggerFactory.getLogger(AuditAspect.class);

  private final AuditEventPublisherPort publisher;
  private final AuditContextProvider contextProvider;
  private final AuditSpelEvaluator spelEvaluator;

  /**
   * Creates the aspect with an explicit context provider.
   *
   * @param publisher the port used to publish audit events
   * @param contextProvider the provider of cross-cutting audit context
   */
  public AuditAspect(AuditEventPublisherPort publisher, AuditContextProvider contextProvider) {
    this.publisher = publisher;
    this.contextProvider = contextProvider;
    this.spelEvaluator = new AuditSpelEvaluator();
  }

  /**
   * Creates the aspect with a {@link NoOpAuditContextProvider}.
   *
   * @param publisher the port used to publish audit events
   */
  public AuditAspect(AuditEventPublisherPort publisher) {
    this(publisher, new NoOpAuditContextProvider());
  }

  /**
   * Wraps an {@link Auditable} method, publishing a success or error {@link AuditEvent} around its
   * execution.
   *
   * @param pjp the intercepted join point
   * @param auditable the annotation on the intercepted method
   * @return the value returned by the intercepted method
   * @throws Throwable if the intercepted method throws
   */
  @Around("@annotation(auditable)")
  public Object around(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
    log.debug("Auditing method: {}", pjp.getSignature().toShortString());

    MethodSignature signature = (MethodSignature) pjp.getSignature();
    Method method = signature.getMethod();
    Object[] args = pjp.getArgs();

    long startTime = System.currentTimeMillis();
    try {
      Object result = pjp.proceed();
      int durationMs = (int) (System.currentTimeMillis() - startTime);
      publishEvent(auditable, method, args, result, STATUS_SUCCESS, true, null, durationMs);
      return result;
    } catch (Throwable ex) {
      int durationMs = (int) (System.currentTimeMillis() - startTime);
      publishEvent(auditable, method, args, null, STATUS_ERROR, false, ex.getMessage(), durationMs);
      throw ex;
    }
  }

  private void publishEvent(
      Auditable auditable,
      Method method,
      Object[] args,
      Object result,
      String status,
      boolean success,
      String errorMessage,
      int durationMs) {
    try {
      EvaluationContext ctx = spelEvaluator.buildContext(method, args, result);

      AuditEvent event =
          new AuditEvent(
              auditable.entityType(),
              resolveEntityId(auditable, ctx),
              auditable.action(),
              resolveActorId(auditable, ctx),
              resolveActorType(auditable),
              contextProvider.getIpAddress().orElse(null),
              resolvePayload(auditable, ctx),
              status,
              resolveCorrelationId(auditable, ctx),
              resolveSourceService(auditable),
              OffsetDateTime.now(),
              contextProvider.getResource().orElse(null),
              null,
              contextProvider.getHttpMethod().orElse(null),
              null,
              contextProvider.getUserAgent().orElse(null),
              null,
              success,
              errorMessage,
              durationMs,
              null);

      publisher.publish(event);
    } catch (Exception ex) {
      log.error("Failed to publish audit event for {}: {}", method.getName(), ex.getMessage(), ex);
    }
  }

  private String resolveEntityId(Auditable auditable, EvaluationContext ctx) {
    return spelEvaluator.evaluate(auditable.entityId(), ctx);
  }

  private String resolveActorId(Auditable auditable, EvaluationContext ctx) {
    String fromAnnotation = spelEvaluator.evaluate(auditable.actorId(), ctx);
    if (fromAnnotation != null && !fromAnnotation.isBlank()) {
      return fromAnnotation;
    }
    return contextProvider.getActorId().orElse(null);
  }

  private String resolveActorType(Auditable auditable) {
    if (!auditable.actorType().isBlank()) {
      return auditable.actorType();
    }
    return contextProvider.getActorType().orElse(null);
  }

  private java.util.Map<String, Object> resolvePayload(Auditable auditable, EvaluationContext ctx) {
    if (auditable.payload().length == 0) {
      return Collections.emptyMap();
    }
    return spelEvaluator.evaluatePayload(auditable.payload(), ctx);
  }

  private UUID resolveCorrelationId(Auditable auditable, EvaluationContext ctx) {
    String value = spelEvaluator.evaluate(auditable.correlationId(), ctx);
    if (value == null || value.isBlank()) {
      return null;
    }
    return UUID.fromString(value);
  }

  private String resolveSourceService(Auditable auditable) {
    if (!auditable.sourceService().isBlank()) {
      return auditable.sourceService();
    }
    return contextProvider.getSourceService().orElse(null);
  }
}
