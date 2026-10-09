package io.codehunters.audit.internal.aspect;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * Evaluates SpEL expressions against method parameters and return values.
 *
 * <p>Parameters resolve by declared name ({@code #name}, needs {@code -parameters}), by position
 * ({@code #p0}, {@code #a0}, {@code #arg0}) and the return value as {@code #result}. {@code
 * #result} is reserved: it is {@code null} when the method failed, even if a parameter is named
 * {@code result}. Parsed expressions are cached up to a fixed bound; once full, new expressions are
 * parsed on every call. Syntax errors, evaluation errors and exceptions thrown by methods the
 * expression calls resolve to {@code null}; the first such failure per method and attribute is
 * logged as a warning naming the exception class or SpEL message code, never the message, which can
 * echo argument values. Later ones are logged at debug level. A {@code #result} expression that
 * fails because the call itself failed, leaving {@code #result} null, is expected and not warned
 * about; write {@code #result?.id} to make that explicit.
 *
 * <p>Expressions run in a sandbox, a read-only {@code SimpleEvaluationContext}: they read
 * properties (getters and record components, {@code #order.id}), index lists and maps ({@code
 * #tags['k']}) and use operators, but call no method, reference no type ({@code T(...)}), call no
 * constructor, resolve no bean ({@code @name}) and assign nothing. Such an expression resolves to
 * {@code null} like any failure; {@link AuditExpressionCheck} reports it at startup.
 *
 * @since 1.0.0
 */
final class AuditSpelEvaluator {

  static final int DEFAULT_CACHE_SIZE = 1024;

  private static final String EXPRESSION = "expression";

  private static final Logger log = LoggerFactory.getLogger(AuditSpelEvaluator.class);

  private final ExpressionParser parser = new SpelExpressionParser();
  private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();
  private final ConcurrentMap<String, Expression> cache = new ConcurrentHashMap<>();
  private final WarnOnce warnOnce = new WarnOnce(log);
  private final int maxCacheSize;

  AuditSpelEvaluator() {
    this(DEFAULT_CACHE_SIZE);
  }

  AuditSpelEvaluator(int maxCacheSize) {
    this.maxCacheSize = maxCacheSize;
  }

  EvaluationContext buildContext(Method method, Object[] args, Object result) {
    return buildContext(method, args, result, false);
  }

  /**
   * Builds the context of one evaluation.
   *
   * @param method the audited method
   * @param args its arguments
   * @param result its return value, or {@code null}
   * @param failed whether the call failed, so a {@code #result} expression is expected to fail
   * @return the context
   */
  EvaluationContext buildContext(Method method, Object[] args, Object result, boolean failed) {
    return new AuditEvaluationContext(
        new AuditEvaluationContext.CapturedArguments(method, args, result, failed), nameDiscoverer);
  }

  String evaluate(String expression, EvaluationContext ctx) {
    return evaluate(EXPRESSION, expression, ctx);
  }

  String evaluate(String attribute, String expression, EvaluationContext ctx) {
    if (expression == null || expression.isBlank()) {
      return null;
    }
    return valueOf(new Site(attribute, expression), ctx, String.class).orElse(null);
  }

  Object evaluateValue(String expression, EvaluationContext ctx) {
    return evaluateValue(EXPRESSION, expression, ctx);
  }

  Object evaluateValue(String attribute, String expression, EvaluationContext ctx) {
    return valueOf(new Site(attribute, expression), ctx, Object.class).orElse(null);
  }

  Map<String, Object> evaluatePayload(String[] expressions, EvaluationContext ctx) {
    Map<String, Object> payload = new LinkedHashMap<>();
    for (String entry : expressions) {
      String[] parts = entry.split("=", 2);
      if (parts.length == 2) {
        Site site = new Site(AuditExpressionCheck.payloadAttribute(parts[0]), parts[1].trim());
        valueOf(site, ctx, Object.class).ifPresent(value -> payload.put(parts[0].trim(), value));
      }
    }
    return payload;
  }

  int cachedExpressions() {
    return cache.size();
  }

  private <T> Optional<T> valueOf(Site site, EvaluationContext ctx, Class<T> type) {
    try {
      return Optional.ofNullable(parse(site.expression()).getValue(ctx, type));
    } catch (ExpressionException ex) {
      log.debug(
          "SpEL expression '{}' could not be resolved: {}", site.expression(), ex.getMessage());
      warn(site, ctx, ex);
      return Optional.empty();
    } catch (RuntimeException ex) {
      log.debug("SpEL expression '{}' threw {}", site.expression(), ex.getClass().getName());
      warn(site, ctx, ex);
      return Optional.empty();
    }
  }

  private void warn(Site site, EvaluationContext ctx, RuntimeException failure) {
    if (expectedFailure(site, ctx)) {
      return;
    }
    String method =
        ctx instanceof AuditEvaluationContext audit ? audit.label() : "an audited method";
    warnOnce.warn(
        method + " " + site.attribute(),
        "An @Auditable expression could not be evaluated for {} of {} ({}); the field is recorded"
            + " as null. Later failures of it are logged at debug level.",
        site.attribute(),
        method,
        reason(failure));
  }

  /**
   * Whether the failure is the expected one of a {@code #result} expression after the call failed,
   * where {@code #result} is {@code null} by design.
   */
  private static boolean expectedFailure(Site site, EvaluationContext ctx) {
    return ctx instanceof AuditEvaluationContext audit
        && audit.failed()
        && site.expression().contains("#" + AuditEvaluationContext.RESULT);
  }

  private static String reason(RuntimeException failure) {
    if (failure instanceof SpelEvaluationException spel) {
      return spel.getMessageCode().name();
    }
    return failure.getClass().getName();
  }

  private Expression parse(String expression) {
    Expression cached = cache.get(expression);
    if (cached != null) {
      return cached;
    }
    Expression parsed = parser.parseExpression(expression);
    if (cache.size() < maxCacheSize) {
      cache.putIfAbsent(expression, parsed);
    }
    return parsed;
  }

  /** An expression and the annotation attribute it was written in. */
  private record Site(String attribute, String expression) {}
}
