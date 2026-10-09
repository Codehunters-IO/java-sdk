package io.codehunters.audit.internal.aspect;

import io.codehunters.audit.Auditable;
import io.codehunters.audit.internal.aspect.AuditableMethods.AuditedMethod;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * Parses every expression of the {@link Auditable} methods of a context, so an expression that
 * would record {@code null} on every call is caught at startup. Only the parser's own message is
 * reported, with the method and attribute; nothing is evaluated.
 */
final class AuditExpressionCheck {

  private static final ExpressionParser PARSER = new SpelExpressionParser();

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
    return Optional.of("Invalid @Auditable expression; the field would always be null:" + invalid);
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
      PARSER.parseExpression(expression);
    } catch (ParseException ex) {
      invalid
          .append(System.lineSeparator())
          .append("  ")
          .append(method.label())
          .append(' ')
          .append(attribute)
          .append(": ")
          .append(ex.getSimpleMessage());
    }
  }
}
