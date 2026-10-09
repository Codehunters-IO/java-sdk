package io.codehunters.audit.internal.aspect;

import io.codehunters.audit.Auditable;
import io.codehunters.audit.internal.aspect.AuditableMethods.AuditedMethod;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.ast.Assign;
import org.springframework.expression.spel.ast.BeanReference;
import org.springframework.expression.spel.ast.ConstructorReference;
import org.springframework.expression.spel.ast.FunctionReference;
import org.springframework.expression.spel.ast.MethodReference;
import org.springframework.expression.spel.ast.OpDec;
import org.springframework.expression.spel.ast.OpInc;
import org.springframework.expression.spel.ast.TypeReference;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * Parses every expression of the {@link Auditable} methods of a context, so an expression that
 * would record {@code null} on every call is caught at startup: one that does not parse, and one
 * that uses what the evaluation sandbox refuses (a method call, a type, a constructor, a bean
 * reference or an assignment). Only the parser's own message or the refused construct is reported,
 * with the method and attribute; nothing is evaluated.
 */
final class AuditExpressionCheck {

  private static final SpelExpressionParser PARSER = new SpelExpressionParser();

  private static final Map<Class<? extends SpelNode>, String> REFUSED =
      Map.of(
          MethodReference.class, "calls a method",
          FunctionReference.class, "calls a method",
          TypeReference.class, "references a type",
          ConstructorReference.class, "calls a constructor",
          BeanReference.class, "references a bean",
          Assign.class, "assigns a value",
          OpInc.class, "assigns a value",
          OpDec.class, "assigns a value");

  private AuditExpressionCheck() {}

  /**
   * Lists the expressions that cannot be parsed.
   *
   * @param audited the context's audited methods
   * @return one line per invalid expression, or empty when all parse
   */
  static Optional<String> problem(List<AuditedMethod> audited) {
    StringBuilder invalid = new StringBuilder();
    for (AuditedMethod method : audited) {
      attributes(method.auditable())
          .forEach((attribute, expression) -> check(method, attribute, expression, invalid));
    }
    if (invalid.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        "Invalid @Auditable expression; the field would always be null (the sandbox only reads"
            + " properties: write #order.id, not #order.getId()):"
            + invalid);
  }

  /**
   * The expression attributes of an annotation, by name; payload entries are named {@code
   * payload[key]}.
   *
   * @param auditable the annotation
   * @return the non-blank expressions, by attribute name
   */
  private static Map<String, String> attributes(Auditable auditable) {
    Map<String, String> attributes = new LinkedHashMap<>();
    putIfPresent(attributes, "entityId", auditable.entityId());
    putIfPresent(attributes, "actorId", auditable.actorId());
    putIfPresent(attributes, "correlationId", auditable.correlationId());
    for (String entry : auditable.payload()) {
      String[] parts = entry.split("=", 2);
      if (parts.length == 2) {
        putIfPresent(attributes, payloadAttribute(parts[0]), parts[1].trim());
      }
    }
    return attributes;
  }

  /**
   * Names a payload entry for messages.
   *
   * @param key the entry's key, as written
   * @return {@code payload[key]}
   */
  static String payloadAttribute(String key) {
    return "payload[" + key.trim() + "]";
  }

  private static void putIfPresent(Map<String, String> attributes, String name, String value) {
    if (!value.isBlank()) {
      attributes.put(name, value);
    }
  }

  private static void check(
      AuditedMethod method, String attribute, String expression, StringBuilder invalid) {
    try {
      SpelExpression parsed = PARSER.parseRaw(expression);
      refused(parsed.getAST()).ifPresent(reason -> report(method, attribute, reason, invalid));
    } catch (ParseException ex) {
      report(method, attribute, ex.getSimpleMessage(), invalid);
    }
  }

  private static void report(
      AuditedMethod method, String attribute, String reason, StringBuilder invalid) {
    invalid
        .append(System.lineSeparator())
        .append("  ")
        .append(method.label())
        .append(' ')
        .append(attribute)
        .append(": ")
        .append(reason);
  }

  /** The first construct of the tree the sandbox refuses, depth first. */
  private static Optional<String> refused(SpelNode node) {
    Optional<String> reason = Optional.ofNullable(REFUSED.get(node.getClass()));
    for (int i = 0; reason.isEmpty() && i < node.getChildCount(); i++) {
      reason = refused(node.getChild(i));
    }
    return reason;
  }
}
