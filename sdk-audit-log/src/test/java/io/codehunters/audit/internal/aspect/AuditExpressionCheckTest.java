package io.codehunters.audit.internal.aspect;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.audit.Auditable;
import io.codehunters.audit.internal.aspect.AuditableMethods.AuditedMethod;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterNameDiscoverer;

class AuditExpressionCheckTest {

  /** Stands for classes compiled without {@code -parameters}: no names are discovered. */
  private static final ParameterNameDiscoverer NO_NAMES =
      new ParameterNameDiscoverer() {
        @Override
        public String[] getParameterNames(Method method) {
          return null;
        }

        @Override
        public String[] getParameterNames(java.lang.reflect.Constructor<?> ctor) {
          return null;
        }
      };

  static class Positional {

    @Auditable(entityType = "ORDER", action = "READ", entityId = "#orderId")
    public void byName(String orderId) {
      // audited only
    }

    @Auditable(entityType = "ORDER", action = "READ", entityId = "#p0", actorId = "#a0")
    public void byPosition(String orderId) {
      // audited only
    }
  }

  @Test
  void should_suggest_positional_variables_when_parameter_names_are_not_available()
      throws NoSuchMethodException {
    var problem = AuditExpressionCheck.problem(audited("byName"), NO_NAMES);

    assertThat(problem)
        .hasValueSatisfying(
            message ->
                assertThat(message)
                    .contains("entityId: references #orderId")
                    .contains("parameter names are not available")
                    .contains("-parameters")
                    .contains("#p0/#a0"));
  }

  @Test
  void should_accept_positional_variables_when_parameter_names_are_not_available()
      throws NoSuchMethodException {
    assertThat(AuditExpressionCheck.problem(audited("byPosition"), NO_NAMES)).isEmpty();
  }

  private static List<AuditedMethod> audited(String name) throws NoSuchMethodException {
    Method method = Positional.class.getMethod(name, String.class);
    return List.of(
        new AuditedMethod(Positional.class, method, method.getAnnotation(Auditable.class)));
  }
}
