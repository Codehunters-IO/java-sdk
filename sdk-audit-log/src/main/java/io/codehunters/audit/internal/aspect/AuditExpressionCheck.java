package io.codehunters.audit.internal.aspect;

import io.codehunters.audit.Auditable;
import io.codehunters.audit.internal.aspect.AuditableMethods.AuditedMethod;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
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
import org.springframework.expression.spel.ast.VariableReference;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * Parses every expression of the {@link Auditable} methods of a context, so an expression that
 * would record {@code null} on every call is caught at startup: one that does not parse, and one
 * that uses what the evaluation sandbox refuses (a method call, a type, a constructor, a bean
 * reference or an assignment), and one that reads a variable the call does not bind, such as a
 * misspelt parameter. Parameter names are discovered as the evaluator does: an application class
 * compiled without {@code -parameters} has none, so a named variable is reported with a hint to
 * compile with {@code -parameters} or use {@code #p0}/{@code #a0}. Only the parser's own message,
 * the refused construct or the unbound variable is reported, with the method and attribute; nothing
 * is evaluated.
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
    return problem(audited, new DefaultParameterNameDiscoverer());
  }

  /**
   * Lists the expressions that cannot be parsed, use what the sandbox refuses, or reference a
   * variable the call does not bind.
   *
   * @param audited the context's audited methods
   * @param names how parameter names are discovered, as the evaluator does
   * @return one line per invalid expression, or empty when all are valid
   */
  static Optional<String> problem(List<AuditedMethod> audited, ParameterNameDiscoverer names) {
    StringBuilder invalid = new StringBuilder();
    for (AuditedMethod method : audited) {
      Variables variables = Variables.of(method.method(), names);
      attributes(method.auditable())
          .forEach(
              (attribute, expression) ->
                  check(new Site(method, attribute, variables), expression, invalid));
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

  private static void check(Site site, String expression, StringBuilder invalid) {
    try {
      SpelNode ast = PARSER.parseRaw(expression).getAST();
      refused(ast)
          .or(() -> unbound(ast, site.variables()))
          .ifPresent(reason -> report(site, reason, invalid));
    } catch (ParseException ex) {
      report(site, ex.getSimpleMessage(), invalid);
    }
  }

  private static void report(Site site, String reason, StringBuilder invalid) {
    invalid
        .append(System.lineSeparator())
        .append("  ")
        .append(site.method().label())
        .append(' ')
        .append(site.attribute())
        .append(": ")
        .append(reason);
  }

  /** The first variable of the tree the call does not bind, depth first. */
  private static Optional<String> unbound(SpelNode node, Variables variables) {
    Optional<String> reason =
        node instanceof VariableReference reference
            ? variables.unbound(reference.toStringAST().substring(1))
            : Optional.empty();
    for (int i = 0; reason.isEmpty() && i < node.getChildCount(); i++) {
      reason = unbound(node.getChild(i), variables);
    }
    return reason;
  }

  /**
   * Where an expression is declared.
   *
   * @param method the audited method
   * @param attribute the attribute, as named in messages
   * @param variables the variables a call of the method binds
   */
  private record Site(AuditedMethod method, String attribute, Variables variables) {}

  /**
   * The variables {@link AuditEvaluationContext} binds for a call: the parameters by name when the
   * names are discoverable, by position as {@code #p0}, {@code #a0} and {@code #arg0}, the return
   * value as {@code #result}, and SpEL's own {@code #root} and {@code #this}.
   *
   * @param names the parameter names, or {@code null} when they cannot be discovered
   * @param bound every variable name a call binds
   */
  private record Variables(String[] names, Set<String> bound) {

    static Variables of(Method method, ParameterNameDiscoverer discoverer) {
      String[] names = discoverer.getParameterNames(method);
      Set<String> bound = new HashSet<>(Set.of(AuditEvaluationContext.RESULT, "root", "this"));
      for (int i = 0; i < method.getParameterCount(); i++) {
        bound.addAll(List.of("p" + i, "a" + i, "arg" + i));
      }
      if (names != null) {
        bound.addAll(Arrays.asList(names));
      }
      return new Variables(names, Set.copyOf(bound));
    }

    Optional<String> unbound(String variable) {
      if (bound.contains(variable)) {
        return Optional.empty();
      }
      if (names == null) {
        return Optional.of(
            "references #"
                + variable
                + ", but parameter names are not available: compile with -parameters or write"
                + " #p0/#a0 by position");
      }
      String parameters = names.length == 0 ? "none" : String.join(", ", names);
      return Optional.of(
          "references #"
              + variable
              + ", which is not a parameter (parameters: "
              + parameters
              + ")");
    }
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
