package io.codehunters.measure.config;

import io.codehunters.measure.Measured;
import io.codehunters.measure.internal.MeasuredDefinition;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Fails application startup when a bean carries a malformed {@link Measured} annotation: tags that
 * are not key/value pairs, an expression that is not {@code key=expression}, a blank, reserved,
 * repeated or malformed tag key, or a count value that is not positive and finite. Expressions that
 * do not parse are logged, not rejected, and recorded as {@code unknown}.
 *
 * <p>Class annotations are checked on the bean's class and its superclasses, since the aspect
 * measures methods declared in an annotated superclass. Startup also fails when two annotations
 * share a meter name but declare different tag keys: registries such as Prometheus accept only one
 * set of keys per name and silently drop the other series. Annotations that are never measured, on
 * private, final or static methods or only on an interface, are logged as warnings. A class that
 * cannot be introspected, for example because a method signature names a missing optional type, is
 * skipped.
 *
 * @since 3.0.0
 */
public class MeasuredBeanValidator implements BeanPostProcessor {

  private static final Logger log = LoggerFactory.getLogger(MeasuredBeanValidator.class);

  private final Map<String, Declaration> declarationsByName = new ConcurrentHashMap<>();

  /** Creates the validator. */
  public MeasuredBeanValidator() {}

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    placements(AopUtils.getTargetClass(bean)).forEach(this::check);
    return bean;
  }

  private static List<Placement> placements(Class<?> type) {
    if (!AnnotationUtils.isCandidateClass(type, Measured.class)) {
      return List.of();
    }
    try {
      List<Placement> placements = new ArrayList<>();
      classPlacements(type, placements);
      methodPlacements(type, placements);
      warnAboutInterfaces(type);
      return placements;
    } catch (Throwable ex) {
      log.debug("Skipping @Measured validation of {}: {}", type.getName(), ex.toString());
      return List.of();
    }
  }

  private static void classPlacements(Class<?> type, List<Placement> placements) {
    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      Measured measured = current.getDeclaredAnnotation(Measured.class);
      if (measured != null) {
        placements.add(new Placement(current.getName(), measured));
      }
    }
  }

  private static void methodPlacements(Class<?> type, List<Placement> placements) {
    ReflectionUtils.doWithMethods(
        type,
        method -> placements.add(methodPlacement(method)),
        method -> method.isAnnotationPresent(Measured.class));
  }

  private static Placement methodPlacement(Method method) {
    String location = method.getDeclaringClass().getName() + "." + method.getName();
    int modifiers = method.getModifiers();
    if (Modifier.isPrivate(modifiers)
        || Modifier.isFinal(modifiers)
        || Modifier.isStatic(modifiers)) {
      log.warn("@Measured on {} is never measured: proxies cannot intercept it", location);
    }
    return new Placement(location, method.getAnnotation(Measured.class));
  }

  private static void warnAboutInterfaces(Class<?> type) {
    for (Class<?> contract : ClassUtils.getAllInterfacesForClassAsSet(type)) {
      if (isAnnotated(contract)) {
        log.warn(
            "@Measured on interface {} is never measured: annotations on interfaces are not inherited",
            contract.getName());
      }
    }
  }

  private static boolean isAnnotated(Class<?> contract) {
    if (contract.isAnnotationPresent(Measured.class)) {
      return true;
    }
    for (Method method : contract.getDeclaredMethods()) {
      if (method.isAnnotationPresent(Measured.class)) {
        return true;
      }
    }
    return false;
  }

  private void check(Placement placement) {
    MeasuredDefinition definition;
    try {
      definition = MeasuredDefinition.of(placement.measured());
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException(
          "Invalid @Measured on " + placement.location() + ": " + ex.getMessage(), ex);
    }
    var declared = new Declaration(placement.location(), definition.tagKeys());
    Declaration first = declarationsByName.putIfAbsent(definition.name(), declared);
    if (first != null && !first.tagKeys().equals(declared.tagKeys())) {
      throw new IllegalStateException(conflict(definition.name(), first, declared));
    }
  }

  private static String conflict(String name, Declaration first, Declaration second) {
    return "@Measured name '"
        + name
        + "' is declared on "
        + first.location()
        + " with tag keys "
        + first.tagKeys()
        + " and on "
        + second.location()
        + " with tag keys "
        + second.tagKeys()
        + "; registries keep one set of tag keys per name. Give one of them an explicit name.";
  }

  private record Placement(String location, Measured measured) {}

  private record Declaration(String location, Set<String> tagKeys) {}
}
