package io.codehunters.weberrors.web;

import io.codehunters.weberrors.FieldViolation;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;

/**
 * Reads Bean Validation's violations. Only loaded when the Bean Validation API is on the classpath,
 * so the handler works without it.
 */
final class ConstraintViolations {

  private static final Set<ElementKind> CLIENT_INPUT =
      Set.of(ElementKind.PARAMETER, ElementKind.CROSS_PARAMETER);

  private ConstraintViolations() {}

  static boolean matches(Exception exception) {
    return exception instanceof ConstraintViolationException;
  }

  /**
   * Whether every violation concerns a method argument. Violations of entities or return values are
   * server-side failures, not client errors.
   */
  static boolean concernsArguments(Exception exception) {
    return violations(exception).stream()
        .allMatch(violation -> concernsArguments(violation.getPropertyPath()));
  }

  static List<FieldViolation> of(Exception exception, ProblemFactory problems) {
    return violations(exception).stream()
        .map(violation -> toViolation(violation, problems))
        .toList();
  }

  /** The interpolated message of the violation behind a Spring error, when free of input. */
  static Optional<String> safeMessage(ObjectError error) {
    return error.contains(ConstraintViolation.class)
        ? safeMessage(error.unwrap(ConstraintViolation.class))
        : Optional.empty();
  }

  /** The interpolated message of the violation behind a method validation error. */
  static Optional<String> safeMessage(
      ParameterValidationResult result, MessageSourceResolvable error) {
    try {
      return safeMessage(result.unwrap(error, ConstraintViolation.class));
    } catch (IllegalArgumentException notAViolation) {
      return Optional.empty();
    }
  }

  /**
   * The interpolated message, unless its template uses an expression: {@code ${validatedValue}} and
   * friends put the rejected value into the message.
   */
  static Optional<String> safeMessage(ConstraintViolation<?> violation) {
    String template = violation.getMessageTemplate();
    return template != null && template.contains("${")
        ? Optional.empty()
        : Optional.ofNullable(violation.getMessage());
  }

  private static Set<ConstraintViolation<?>> violations(Exception exception) {
    return ((ConstraintViolationException) exception).getConstraintViolations();
  }

  private static boolean concernsArguments(Path path) {
    return StreamSupport.stream(path.spliterator(), false)
        .anyMatch(node -> CLIENT_INPUT.contains(node.getKind()));
  }

  private static FieldViolation toViolation(
      ConstraintViolation<?> violation, ProblemFactory problems) {
    String constraint =
        violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
    var resolvable = new DefaultMessageSourceResolvable(new String[] {constraint}, null, null);
    return problems.violation(
        violation.getPropertyPath().toString(), resolvable, safeMessage(violation));
  }
}
