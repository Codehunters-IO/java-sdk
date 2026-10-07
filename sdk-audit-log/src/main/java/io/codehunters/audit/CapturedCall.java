package io.codehunters.audit;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.expression.EvaluationContext;

/**
 * What an audit event needs from the calling thread, taken when the audited method returns: the
 * {@link AuditContextProvider} fields, the MDC, and every expression that does not name {@code
 * #result}. Only expressions naming {@code #result} are evaluated later, once the outcome is known,
 * so work that completes on another thread still records the caller's actor, request and argument
 * values.
 */
final class CapturedCall {

  private static final Logger log = LoggerFactory.getLogger(CapturedCall.class);
  private static final String RESULT = "#result";
  private static final Kind TEXT = Kind.TEXT;
  private static final Kind VALUE = Kind.VALUE;

  private final Auditable auditable;
  private final Method method;
  private final Object[] args;
  private final AuditSpelEvaluator spel;
  private final Map<String, Optional<Object>> resolved = new LinkedHashMap<>();
  private final ProviderFields provider;
  private final Map<String, String> mdc;

  private CapturedCall(Auditable auditable, Method method, Object[] args, Capture capture) {
    this.auditable = auditable;
    this.method = method;
    this.args = args;
    this.spel = capture.spel();
    this.provider = ProviderFields.of(capture.contextProvider());
    this.mdc = capture.withMdc() ? MDC.getCopyOfContextMap() : null;
    resolveEarly();
  }

  static CapturedCall of(Auditable auditable, Method method, Object[] args, Capture capture) {
    return new CapturedCall(auditable, method, args, capture);
  }

  Method method() {
    return method;
  }

  Auditable auditable() {
    return auditable;
  }

  ProviderFields provider() {
    return provider;
  }

  Map<String, String> mdc() {
    return mdc;
  }

  /**
   * Resolves the annotation's expressions against the outcome.
   *
   * @param result the value the call produced, or {@code null} if it failed
   * @return the resolved values
   */
  Resolved resolve(Object result) {
    EvaluationContext late = spel.buildContext(method, args, result);
    return new Resolved(
        text(auditable.entityId(), late),
        text(auditable.actorId(), late),
        text(auditable.correlationId(), late),
        payload(late));
  }

  private void resolveEarly() {
    EvaluationContext early = spel.buildContext(method, args, null);
    capture(TEXT, auditable.entityId(), early);
    capture(TEXT, auditable.actorId(), early);
    capture(TEXT, auditable.correlationId(), early);
    for (String entry : auditable.payload()) {
      String[] parts = entry.split("=", 2);
      if (parts.length == 2) {
        capture(VALUE, parts[1].trim(), early);
      }
    }
  }

  private void capture(Kind kind, String expression, EvaluationContext early) {
    if (expression == null || expression.isBlank() || expression.contains(RESULT)) {
      return;
    }
    resolved.computeIfAbsent(
        kind + expression, key -> Optional.ofNullable(kind.evaluate(spel, expression, early)));
  }

  private String text(String expression, EvaluationContext late) {
    return (String) value(TEXT, expression, late);
  }

  private Object value(Kind kind, String expression, EvaluationContext late) {
    if (expression == null || expression.isBlank()) {
      return null;
    }
    Optional<Object> early = resolved.get(kind + expression);
    if (early != null) {
      return early.orElse(null);
    }
    return kind.evaluate(spel, expression, late);
  }

  private Map<String, Object> payload(EvaluationContext late) {
    Map<String, Object> payload = new LinkedHashMap<>();
    for (String entry : auditable.payload()) {
      String[] parts = entry.split("=", 2);
      if (parts.length == 2) {
        Object value = value(VALUE, parts[1].trim(), late);
        if (value != null) {
          payload.put(parts[0].trim(), value);
        }
      }
    }
    return payload;
  }

  /** Whether an expression yields a string field or a payload value. */
  private enum Kind {
    TEXT,
    VALUE;

    Object evaluate(AuditSpelEvaluator spel, String expression, EvaluationContext ctx) {
      return this == TEXT ? spel.evaluate(expression, ctx) : spel.evaluateValue(expression, ctx);
    }
  }

  /**
   * How to capture a call.
   *
   * @param spel the expression evaluator
   * @param contextProvider the provider of cross-cutting context
   * @param withMdc whether to copy the MDC, for events published from another thread
   */
  record Capture(AuditSpelEvaluator spel, AuditContextProvider contextProvider, boolean withMdc) {}

  /**
   * Values of the annotation's expressions.
   *
   * @param entityId the entity id
   * @param actorId the actor id from the annotation
   * @param correlationId the correlation id, unparsed
   * @param payload the payload entries that resolved to a value
   */
  record Resolved(
      String entityId, String actorId, String correlationId, Map<String, Object> payload) {}

  /** The {@link AuditContextProvider} fields, read on the calling thread. */
  record ProviderFields(
      String actorId,
      String actorType,
      String ipAddress,
      String sourceService,
      String resource,
      String httpMethod,
      String userAgent) {

    static ProviderFields of(AuditContextProvider provider) {
      return new ProviderFields(
          read(provider::getActorId),
          read(provider::getActorType),
          read(provider::getIpAddress),
          read(provider::getSourceService),
          read(provider::getResource),
          read(provider::getHttpMethod),
          read(provider::getUserAgent));
    }

    private static String read(Supplier<Optional<String>> field) {
      try {
        return field.get().orElse(null);
      } catch (RuntimeException ex) {
        log.debug("Audit context provider threw {}", ex.getClass().getName());
        return null;
      }
    }
  }
}
