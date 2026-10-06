package io.codehunters.audit;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * Evaluates SpEL expressions against method parameters and return values.
 *
 * <p>Parameters resolve by declared name ({@code #name}, needs {@code -parameters}), by position
 * ({@code #p0}, {@code #a0}, {@code #arg0}) and the return value as {@code #result}. Parsed
 * expressions are cached up to a fixed bound; once full, new expressions are parsed on every call.
 * Syntax and evaluation errors resolve to {@code null}.
 *
 * @since 1.0.0
 */
final class AuditSpelEvaluator {

  static final int DEFAULT_CACHE_SIZE = 1024;

  private static final Logger log = LoggerFactory.getLogger(AuditSpelEvaluator.class);

  private final ExpressionParser parser = new SpelExpressionParser();
  private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();
  private final ConcurrentMap<String, Expression> cache = new ConcurrentHashMap<>();
  private final int maxCacheSize;

  AuditSpelEvaluator() {
    this(DEFAULT_CACHE_SIZE);
  }

  AuditSpelEvaluator(int maxCacheSize) {
    this.maxCacheSize = maxCacheSize;
  }

  EvaluationContext buildContext(Method method, Object[] args, Object result) {
    var ctx = new MethodBasedEvaluationContext(null, method, args, nameDiscoverer);
    for (int i = 0; i < args.length; i++) {
      ctx.setVariable("arg" + i, args[i]);
    }
    ctx.setVariable("result", result);
    return ctx;
  }

  String evaluate(String expression, EvaluationContext ctx) {
    if (expression == null || expression.isBlank()) {
      return null;
    }
    return valueOf(expression, ctx, String.class).orElse(null);
  }

  Map<String, Object> evaluatePayload(String[] expressions, EvaluationContext ctx) {
    Map<String, Object> payload = new LinkedHashMap<>();
    for (String entry : expressions) {
      String[] parts = entry.split("=", 2);
      if (parts.length == 2) {
        valueOf(parts[1].trim(), ctx, Object.class)
            .ifPresent(value -> payload.put(parts[0].trim(), value));
      }
    }
    return payload;
  }

  int cachedExpressions() {
    return cache.size();
  }

  private <T> Optional<T> valueOf(String expression, EvaluationContext ctx, Class<T> type) {
    try {
      return Optional.ofNullable(parse(expression).getValue(ctx, type));
    } catch (ExpressionException ex) {
      log.debug("SpEL expression '{}' could not be resolved: {}", expression, ex.getMessage());
      return Optional.empty();
    }
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
}
