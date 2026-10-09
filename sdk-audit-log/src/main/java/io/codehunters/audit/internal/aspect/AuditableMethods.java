package io.codehunters.audit.internal.aspect;

import io.codehunters.audit.Auditable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;

/**
 * The {@link Auditable} methods of the beans of a context. A bean already created is inspected
 * through its target class, so a proxy is seen through; one not created yet, such as a lazy bean,
 * by its declared type, without creating it.
 */
final class AuditableMethods {

  private AuditableMethods() {}

  /**
   * Finds every audited method of the context's beans.
   *
   * @param beanFactory the context's bean factory
   * @return the audited methods, in bean definition order
   */
  static List<AuditedMethod> in(ConfigurableListableBeanFactory beanFactory) {
    List<AuditedMethod> found = new ArrayList<>();
    for (String name : beanFactory.getBeanDefinitionNames()) {
      Class<?> type = typeOf(beanFactory, name);
      if (type != null && AnnotationUtils.isCandidateClass(type, Auditable.class)) {
        annotated(type)
            .forEach((method, auditable) -> found.add(new AuditedMethod(type, method, auditable)));
      }
    }
    return List.copyOf(found);
  }

  private static Class<?> typeOf(ConfigurableListableBeanFactory beanFactory, String name) {
    Object created = beanFactory.getSingleton(name);
    if (created != null) {
      return AopUtils.getTargetClass(created);
    }
    Class<?> declared = beanFactory.getType(name, false);
    return declared == null ? null : ClassUtils.getUserClass(declared);
  }

  private static Map<Method, Auditable> annotated(Class<?> type) {
    return MethodIntrospector.selectMethods(
        type,
        (MethodIntrospector.MetadataLookup<Auditable>)
            method -> AnnotatedElementUtils.findMergedAnnotation(method, Auditable.class));
  }

  /**
   * One audited method.
   *
   * @param beanType the class of the bean that declares it
   * @param method the method
   * @param auditable its annotation
   */
  record AuditedMethod(Class<?> beanType, Method method, Auditable auditable) {

    /**
     * Names the method for a log line or failure message.
     *
     * @return {@code ClassName#methodName}
     */
    String label() {
      return beanType.getName() + "#" + method.getName();
    }
  }
}
