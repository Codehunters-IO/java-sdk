package io.codehunters.audit;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * Evaluates SpEL expressions against method parameters and return values.
 *
 * @since 1.0.0
 */
final class AuditSpelEvaluator {

  private static final Logger log = LoggerFactory.getLogger(AuditSpelEvaluator.class);

  private final ExpressionParser parser = new SpelExpressionParser();
  private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

  EvaluationContext buildContext(Method method, Object[] args, Object result) {
    StandardEvaluationContext ctx = new StandardEvaluationContext();
    String[] paramNames = nameDiscoverer.getParameterNames(method);

    if (paramNames == null) {
      paramNames = defaultParamNames(args.length);
    }

    for (int i = 0; i < args.length; i++) {
      ctx.setVariable("p" + i, args[i]);
      ctx.setVariable("a" + i, args[i]);
      ctx.setVariable("arg" + i, args[i]);
      ctx.setVariable(paramNames[i], args[i]);
    }

    ctx.setVariable("result", result);

    return ctx;
  }

  String evaluate(String expression, EvaluationContext ctx) {
    if (expression == null || expression.isBlank()) {
      return null;
    }
    try {
      return parser.parseExpression(expression).getValue(ctx, String.class);
    } catch (EvaluationException ex) {
      log.debug("SpEL expression '{}' could not be resolved: {}", expression, ex.getMessage());
      return null;
    }
  }

  Map<String, Object> evaluatePayload(String[] expressions, EvaluationContext ctx) {
    Map<String, Object> payload = new LinkedHashMap<>();
    for (String expr : expressions) {
      String[] parts = expr.split("=", 2);
      if (parts.length == 2) {
        String key = parts[0].trim();
        try {
          Object value = parser.parseExpression(parts[1].trim()).getValue(ctx);
          if (value != null) {
            payload.put(key, value);
          }
        } catch (EvaluationException ex) {
          log.debug("SpEL payload '{}' could not be resolved: {}", key, ex.getMessage());
        }
      }
    }
    return payload;
  }

  private String[] defaultParamNames(int count) {
    String[] names = new String[count];
    for (int i = 0; i < count; i++) {
      names[i] = "arg" + i;
    }
    return names;
  }
}
