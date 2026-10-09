package io.codehunters.audit.internal.aspect;

import io.codehunters.audit.AuditContextProvider;
import io.codehunters.audit.AuditEvent;
import io.codehunters.audit.AuditEventPublisherPort;
import io.codehunters.audit.AuditPersistenceException;
import io.codehunters.audit.Auditable;
import io.codehunters.audit.NoOpAuditContextProvider;
import io.codehunters.audit.internal.reactive.AsyncResults;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;

/**
 * Intercepts methods annotated with {@link Auditable} and publishes an {@link AuditEvent} via the
 * configured {@link AuditEventPublisherPort}.
 *
 * <p>A failing method is recorded with the class name of its exception, never its message, which
 * may carry personal data. In fail-on-error mode, an event that cannot be built or published fails
 * the call with a {@linkplain AuditPersistenceException#redacted redacted} {@link
 * AuditPersistenceException}; on success it replaces the result. When the method itself failed,
 * anything the publisher throws is added as suppressed to the method's exception, which is rethrown
 * unchanged. Without fail-on-error, a failure is logged with its exception class only.
 *
 * <p>An expression or {@link AuditContextProvider} call that fails leaves only its own field {@code
 * null}. The provider fields, the MDC and every expression that does not name {@code #result} are
 * taken on the calling thread when the method returns; see {@link CapturedCall}.
 *
 * <p>A method that returns a {@link java.util.concurrent.CompletionStage}, or a Reactor {@code
 * Mono} or {@code Flux}, is audited when that work finishes, with its outcome and the time it took.
 * A cancelled future or subscription is recorded with status {@code CANCELLED}. The stage is
 * returned unchanged; a {@code Mono} or {@code Flux} is returned decorated and audits each
 * subscription, so a retry or resubscription records one event per attempt. A method declared to
 * return a subclass such as {@code ConnectableFlux} is audited when it returns. The completion
 * event is published {@linkplain AuditEventPublisherPort#publishDetached detached} from any
 * transaction on the completing thread, with the caller's MDC, and cannot fail the call: a failure
 * to publish it is logged, even in fail-on-error mode. When neither the annotation nor the provider
 * names an actor and Spring Security is present, a {@code Mono} or {@code Flux} event takes the
 * authenticated user of the subscriber's Reactor context, where WebFlux keeps it. Failing that, an
 * event takes the authenticated, non-anonymous user of the calling thread's {@code
 * SecurityContextHolder}, where a servlet application keeps it, read when the method returns.
 *
 * <p>The aspect is {@link Ordered}. With the default {@link Ordered#LOWEST_PRECEDENCE} it runs
 * inside the transaction of a {@code @Transactional} method, so a synchronous write of a success
 * event commits or rolls back with the business write.
 *
 * @since 1.0.0
 */
@Aspect
public class AuditAspect implements Ordered {

  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final String STATUS_ERROR = "ERROR";
  private static final String STATUS_CANCELLED = "CANCELLED";

  private final Logger log = LoggerFactory.getLogger(AuditAspect.class);

  private final AuditEventPublisherPort publisher;
  private final AuditContextProvider contextProvider;
  private final AuditSpelEvaluator spelEvaluator;
  private final Options options;

  /**
   * Creates the aspect.
   *
   * @param publisher the port used to publish audit events
   * @param contextProvider the provider of cross-cutting audit context
   * @param options the clock, failure mode and order of the aspect
   * @since 3.0.0
   */
  public AuditAspect(
      AuditEventPublisherPort publisher, AuditContextProvider contextProvider, Options options) {
    this.publisher = publisher;
    this.contextProvider = contextProvider;
    this.options = options;
    this.spelEvaluator = new AuditSpelEvaluator();
  }

  /**
   * Creates the aspect with the given clock, without fail-on-error and with the lowest precedence.
   *
   * @param publisher the port used to publish audit events
   * @param contextProvider the provider of cross-cutting audit context
   * @param clock the clock that stamps {@link AuditEvent#createdAt()}
   * @since 3.0.0
   */
  public AuditAspect(
      AuditEventPublisherPort publisher, AuditContextProvider contextProvider, Clock clock) {
    this(publisher, contextProvider, new Options(clock, false, Ordered.LOWEST_PRECEDENCE));
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

  @Override
  public int getOrder() {
    return options.order();
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
      publishAfterFailure(ex, () -> publish(capture(auditable, method, args, false), ex, start));
      throw ex;
    }
    return observeOrPublish(capture(auditable, method, args, true), result, start);
  }

  private Object observeOrPublish(CapturedCall call, Object result, long start) {
    Optional<Object> pending =
        AsyncResults.observe(result, call.method().getReturnType(), start, completion(call));
    if (pending.isPresent()) {
      return pending.get();
    }
    publish(call, success(result), start, false);
    return result;
  }

  private CapturedCall capture(
      Auditable auditable, Method method, Object[] args, boolean mayComplete) {
    return CapturedCall.of(
        auditable,
        method,
        args,
        new CapturedCall.Capture(spelEvaluator, contextProvider, mayComplete));
  }

  private void publish(CapturedCall call, Throwable failure, long startNanos) {
    publish(call, failure(failure), startNanos, false);
  }

  private AsyncResults.Completion completion(CapturedCall call) {
    return (value, failure, startNanos, subscriberActor) -> {
      Outcome outcome =
          (failure == null ? success(value) : failure(failure)).withActor(subscriberActor);
      Map<String, String> previous = MDC.getCopyOfContextMap();
      AuditMdcTaskDecorator.replaceMdc(call.mdc());
      try {
        publish(call, outcome, startNanos, true);
      } catch (VirtualMachineError ex) {
        throw ex;
      } catch (Throwable ex) {
        log.error(
            "Failed to audit the completion of {}: {}",
            call.method().getName(),
            ex.getClass().getName());
      } finally {
        AuditMdcTaskDecorator.replaceMdc(previous);
      }
    };
  }

  private static Outcome success(Object result) {
    return new Outcome(result, STATUS_SUCCESS, true, null, null);
  }

  private static Outcome failure(Throwable failure) {
    String status = failure instanceof CancellationException ? STATUS_CANCELLED : STATUS_ERROR;
    return new Outcome(null, status, false, failure.getClass().getName(), null);
  }

  private static void publishAfterFailure(Throwable methodFailure, Runnable publish) {
    try {
      publish.run();
    } catch (Throwable ex) {
      methodFailure.addSuppressed(ex);
    }
  }

  private void publish(CapturedCall call, Outcome outcome, long startNanos, boolean detached) {
    int durationMs = elapsedMillis(startNanos);
    AuditEvent event;
    try {
      event = buildEvent(call, outcome, durationMs);
    } catch (RuntimeException ex) {
      onFailure("build", call, ex);
      return;
    }
    try {
      deliver(event, detached);
    } catch (AuditPersistenceException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      onFailure("publish", call, ex);
    }
  }

  private void deliver(AuditEvent event, boolean detached) {
    if (detached) {
      publisher.publishDetached(event);
    } else {
      publisher.publish(event);
    }
  }

  private void onFailure(String step, CapturedCall call, RuntimeException failure) {
    if (options.failOnError()) {
      throw AuditPersistenceException.redacted("Failed to " + step + " audit event", failure);
    }
    log.error(
        "Failed to {} audit event for {}: {}",
        step,
        call.method().getName(),
        failure.getClass().getName());
  }

  private AuditEvent buildEvent(CapturedCall call, Outcome outcome, int durationMs) {
    Auditable auditable = call.auditable();
    CapturedCall.ProviderFields provider = call.provider();
    CapturedCall.Resolved resolved = call.resolve(outcome.result(), !outcome.success());
    return new AuditEvent(
        auditable.entityType(),
        resolved.entityId(),
        auditable.action(),
        firstNonBlank(
            firstNonBlank(resolved.actorId(), provider.actorId()),
            firstNonBlank(outcome.actor(), call.threadActor())),
        firstNonBlank(auditable.actorType(), provider.actorType()),
        provider.ipAddress(),
        resolved.payload(),
        outcome.status(),
        toUuid(resolved.correlationId()),
        firstNonBlank(auditable.sourceService(), provider.sourceService()),
        OffsetDateTime.now(options.clock()),
        provider.resource(),
        null,
        provider.httpMethod(),
        null,
        provider.userAgent(),
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

  private static String firstNonBlank(String preferred, String fallback) {
    return preferred != null && !preferred.isBlank() ? preferred : fallback;
  }

  private UUID toUuid(String value) {
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

  /**
   * Settings of the aspect.
   *
   * @param clock the clock that stamps {@link AuditEvent#createdAt()}
   * @param failOnError whether an event that cannot be built fails the audited call
   * @param order the aspect's precedence among other advice; see {@link Ordered}
   * @since 3.0.0
   */
  public record Options(Clock clock, boolean failOnError, int order) {}

  /**
   * How the call ended.
   *
   * @param actor the authenticated user of a Reactor subscriber, used when the annotation and the
   *     {@link AuditContextProvider} name none
   */
  private record Outcome(
      Object result, String status, boolean success, String error, String actor) {

    Outcome withActor(String subscriberActor) {
      return new Outcome(result, status, success, error, subscriberActor);
    }
  }
}
