package io.codehunters.measure;

import java.lang.reflect.AnnotatedElement;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.util.ReflectionUtils;

/**
 * Fails application startup when a bean carries a malformed {@link Measured} annotation: tags that
 * are not key/value pairs, an expression that is not {@code key=expression}, or a blank, reserved
 * or repeated tag key. Expressions that do not parse are logged, not rejected, and recorded as
 * {@code unknown}.
 *
 * @since 3.0.0
 */
public class MeasuredBeanValidator implements BeanPostProcessor {

  /** Creates the validator. */
  public MeasuredBeanValidator() {}

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    Class<?> type = AopUtils.getTargetClass(bean);
    validate(type, type.getName());
    ReflectionUtils.doWithMethods(
        type,
        method -> validate(method, type.getName() + "." + method.getName()),
        method -> method.isAnnotationPresent(Measured.class));
    return bean;
  }

  private static void validate(AnnotatedElement element, String location) {
    Measured measured = element.getAnnotation(Measured.class);
    if (measured == null) {
      return;
    }
    try {
      MeasuredDefinition.of(measured);
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException(
          "Invalid @Measured on " + location + ": " + ex.getMessage(), ex);
    }
  }
}
