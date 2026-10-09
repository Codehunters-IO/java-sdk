package io.codehunters.measure.internal;

import io.micrometer.core.instrument.Tags;
import java.lang.reflect.Method;
import org.springframework.core.ParameterNameDiscoverer;

/**
 * Everything about measuring one method that does not change between calls, worked out on its first
 * call: the parsed annotation, the tags it always carries, its parameter names and whether any
 * expression needs an evaluation context.
 *
 * @param definition the parsed annotation, or {@code null} if it is invalid
 * @param site the meter name and method, for limits and log messages
 * @param baseTags the {@code class}, {@code method} and static tags
 * @param parameterNames the method's parameter names, or {@code null} if unknown
 * @param needsContext whether an expression tag or count condition is declared
 */
record MethodPlan(
    MeasuredDefinition definition,
    String site,
    Tags baseTags,
    String[] parameterNames,
    boolean needsContext) {

  static final MethodPlan INVALID = new MethodPlan(null, "", Tags.empty(), null, false);

  static MethodPlan of(
      MeasuredDefinition definition, Method method, ParameterNameDiscoverer parameterNames) {
    return new MethodPlan(
        definition,
        "'" + definition.name() + "' at " + location(method),
        Tags.of("class", method.getDeclaringClass().getName())
            .and("method", method.getName())
            .and(definition.staticTags()),
        parameterNames.getParameterNames(method),
        !definition.expressionTags().isEmpty() || definition.countCondition() != null);
  }

  static String location(Method method) {
    return method.getDeclaringClass().getSimpleName() + "." + method.getName();
  }

  boolean isValid() {
    return definition != null;
  }
}
