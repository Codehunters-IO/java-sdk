package io.codehunters.audit;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
 * <p>A failing method is recorded with the class name of its exception, never its message, which
 * may carry personal data. An {@link AuditPersistenceException} from the publisher (fail-on-error
 * mode) is propagated: on success it replaces the result, on failure it is added as suppressed to
 * the method's exception. Any other publisher failure is logged and the call continues.
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
  private final Clock clock;

  /**
   * Creates the aspect.
   *
   * @param publisher the port used to publish audit events
   * @param contextProvider the provider of cross-cutting audit context
   * @param clock the clock that stamps {@link AuditEvent#createdAt()}
   * @since 3.0.0
   */
  public AuditAspect(
      AuditEventPublisherPort publisher, AuditContextProvider contextProvider, Clock clock) {
    this.publisher = publisher;
    this.contextProvider = contextProvider;
    this.clock = clock;
    this.spelEvaluator = new AuditSpelEvaluator();
  }

  /**
   * Creates the aspect with the system UTC clock.
   *
   * @param publisher the port used to publish audit events
   * @param contextProvider the provider of cross-cutting audit context
   */
  public AuditAspect(AuditEventPublisherPort publisher, AuditContextProvider contextProvider) {
    this(publisher, contextProvider, Clock.systemUTC());
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

    Method method = ((MethodSignature) pjp.getSignature()).getMethod();
    Object[] args = pjp.getArgs();
    long start = System.nanoTime();
    Object result;
    try {
      result = pjp.proceed();
    } catch (Throwable ex) {
      Outcome failure = new Outcome(null, STATUS_ERROR, false, ex.getClass().getName());
      publishAfterFailure(ex, () -> publishEvent(auditable, method, args, failure, start));
      throw ex;
    }
    publishEvent(auditable, method, args, new Outcome(result, STATUS_SUCCESS, true, null), start);
    return result;
  }

  private static void publishAfterFailure(Throwable methodFailure, Runnable publish) {
    try {
      publish.run();
    } catch (AuditPersistenceException ex) {
      methodFailure.addSuppressed(ex);
    }
  }

  private void publishEvent(
      Auditable auditable, Method method, Object[] args, Outcome outcome, long startNanos) {
    int durationMs = elapsedMillis(startNanos);
    AuditEvent event;
    try {
      event = buildEvent(auditable, method, args, outcome, durationMs);
    } catch (RuntimeException ex) {
      log.error("Failed to build audit event for {}", method.getName(), ex);
      return;
    }
    publish(event, method);
  }

  private void publish(AuditEvent event, Method method) {
    try {
      publisher.publish(event);
    } catch (AuditPersistenceException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      log.error("Failed to publish audit event for {}", method.getName(), ex);
    }
  }

  private AuditEvent buildEvent(
      Auditable auditable, Method method, Object[] args, Outcome outcome, int durationMs) {
    EvaluationContext ctx = spelEvaluator.buildContext(method, args, outcome.result());
    return new AuditEvent(
        auditable.entityType(),
        resolveEntityId(auditable, ctx),
        auditable.action(),
        resolveActorId(auditable, ctx),
        resolveActorType(auditable),
        contextProvider.getIpAddress().orElse(null),
        resolvePayload(auditable, ctx),
        outcome.status(),
        resolveCorrelationId(auditable, ctx),
        resolveSourceService(auditable),
        OffsetDateTime.now(clock),
        contextProvider.getResource().orElse(null),
        null,
        contextProvider.getHttpMethod().orElse(null),
        null,
        contextProvider.getUserAgent().orElse(null),
        null,
        outcome.success(),
        outcome.error(),
        durationMs,
        null);
  }

  private static int elapsedMillis(long startNanos) {
    long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    return (int) Math.min(millis, Integer.MAX_VALUE);
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

  private Map<String, Object> resolvePayload(Auditable auditable, EvaluationContext ctx) {
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
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException ex) {
      log.debug("Correlation id is not a UUID; recording the event without it");
      return null;
    }
  }

  private String resolveSourceService(Auditable auditable) {
    if (!auditable.sourceService().isBlank()) {
      return auditable.sourceService();
    }
    return contextProvider.getSourceService().orElse(null);
  }

  private record Outcome(Object result, String status, boolean success, String error) {}
}
