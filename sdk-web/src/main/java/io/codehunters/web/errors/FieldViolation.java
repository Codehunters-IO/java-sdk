package io.codehunters.web.errors;

/**
 * One failed constraint, as listed in the {@code errors} extension. It never carries the rejected
 * value, which may be a secret or personal data.
 *
 * @param field the property path, e.g. {@code items[0].quantity}; for object-level errors the
 *     object name, for cross-parameter errors an empty string
 * @param code the constraint name, e.g. {@code NotBlank}
 * @param message the localized message
 */
public record FieldViolation(String field, String code, String message) {

  /** Validates that every component is present. */
  public FieldViolation {
    requirePresent(field, "field");
    requirePresent(code, "code");
    requirePresent(message, "message");
  }

  private static void requirePresent(String value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
  }
}
