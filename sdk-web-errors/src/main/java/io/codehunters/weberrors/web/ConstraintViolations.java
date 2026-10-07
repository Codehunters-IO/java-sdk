package io.codehunters.weberrors.web;

import io.codehunters.weberrors.FieldViolation;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;

/**
 * Reads Bean Validation's {@link ConstraintViolationException}. Only loaded when the Bean
 * Validation API is on the classpath, so the handler works without it.
 */
final class ConstraintViolations {

  private ConstraintViolations() {}

  static boolean matches(Exception exception) {
    return exception instanceof ConstraintViolationException;
  }

  static List<FieldViolation> of(Exception exception) {
    return ((ConstraintViolationException) exception)
        .getConstraintViolations().stream().map(ConstraintViolations::toViolation).toList();
  }

  private static FieldViolation toViolation(ConstraintViolation<?> violation) {
    String constraint =
        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
    return new FieldViolation(
        violation.getPropertyPath().toString(), constraint, violation.getMessage());
  }
}
