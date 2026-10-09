package io.codehunters.audit.internal.aspect;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Names an audited method the same way in startup checks and run-time warnings: declaring class,
 * method name and parameter types, so overloads are told apart.
 */
final class MethodLabels {

  private MethodLabels() {}

  /**
   * Names a method for a log line or failure message.
   *
   * @param method the method
   * @return {@code ClassName#method(ParamType, ...)}
   */
  static String of(Method method) {
    String parameters =
        Arrays.stream(method.getParameterTypes())
            .map(Class::getSimpleName)
            .collect(Collectors.joining(", "));
    return method.getDeclaringClass().getName() + "#" + method.getName() + "(" + parameters + ")";
  }
}
