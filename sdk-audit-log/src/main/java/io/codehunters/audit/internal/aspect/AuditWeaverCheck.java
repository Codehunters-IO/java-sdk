package io.codehunters.audit.internal.aspect;

import io.codehunters.audit.internal.aspect.AuditableMethods.AuditedMethod;
import java.util.List;
import java.util.Optional;
import org.springframework.util.ClassUtils;

/**
 * Explains why {@link io.codehunters.audit.Auditable} methods go unaudited when a publisher exists
 * but AspectJ's weaver is missing: Boot's AOP auto-configuration then creates no AspectJ proxy, so
 * the aspect never applies.
 */
final class AuditWeaverCheck {

  private static final String WEAVER = "org.aspectj.weaver.Advice";

  private AuditWeaverCheck() {}

  /**
   * Describes the gap, if there is one.
   *
   * @param audited the context's audited methods
   * @param hasPublisher whether a publisher exists; without one the publisher check reports
   * @param classLoader the context's class loader
   * @return why the aspect cannot apply, or empty when the weaver is present or nothing is audited
   */
  static Optional<String> problem(
      List<AuditedMethod> audited, boolean hasPublisher, ClassLoader classLoader) {
    if (audited.isEmpty() || !hasPublisher || ClassUtils.isPresent(WEAVER, classLoader)) {
      return Optional.empty();
    }
    return Optional.of(
        "Auditing is inactive: @Auditable methods exist (for example "
            + audited.getFirst().label()
            + "), but aspectjweaver is not on the classpath, so the audit aspect cannot apply."
            + " Add org.aspectj:aspectjweaver to the runtime classpath.");
  }
}
