package io.codehunters.measure;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * A SpEL expression from a {@link Measured} annotation, parsed once. An expression that cannot be
 * parsed or evaluated never fails the measured call: a tag gets {@value #UNKNOWN} and a count
 * condition does not hold. The first failure of each expression is logged with the expression and
 * the exception class, never with argument values.
 */
final class MeasuredExpression {

  static final String NONE = "none";
  static final String UNKNOWN = "unknown";

  private static final Logger log = LoggerFactory.getLogger(MeasuredExpression.class);
  private static final ExpressionParser PARSER = new SpelExpressionParser();

  private final String source;
  private final Expression expression;
  private final AtomicBoolean warned = new AtomicBoolean();

  private MeasuredExpression(String source, Expression expression) {
    this.source = source;
    this.expression = expression;
  }

  static MeasuredExpression parse(String source) {
    try {
      return new MeasuredExpression(source, PARSER.parseExpression(source));
    } catch (ParseException ex) {
      MeasuredExpression unparseable = new MeasuredExpression(source, null);
      unparseable.warnOnce(ex);
      return unparseable;
    }
  }

  /**
   * Evaluates the expression as a tag value.
   *
   * @param context the variables of the call
   * @return the value; {@value #NONE} when it is null or blank, {@value #UNKNOWN} when it fails
   */
  String tagValue(EvaluationContext context) {
    try {
      String value = evaluate(context, String.class);
      return value == null || value.isBlank() ? NONE : value;
    } catch (RuntimeException ex) {
      warnOnce(ex);
      return UNKNOWN;
    }
  }

  /**
   * Evaluates the expression as a condition.
   *
   * @param context the variables of the call
   * @return whether it evaluated to {@code true}; {@code false} when it fails
   */
  boolean holds(EvaluationContext context) {
    try {
      return Boolean.TRUE.equals(evaluate(context, Boolean.class));
    } catch (RuntimeException ex) {
      warnOnce(ex);
      return false;
    }
  }

  private <T> T evaluate(EvaluationContext context, Class<T> type) {
    if (expression == null) {
      throw new IllegalStateException("unparseable expression");
    }
    return expression.getValue(context, type);
  }

  private void warnOnce(Exception failure) {
    if (warned.compareAndSet(false, true)) {
      log.warn(
          "@Measured expression '{}' failed and is ignored: {}",
          source,
          failure.getClass().getName());
    }
  }
}
