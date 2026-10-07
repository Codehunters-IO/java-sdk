package io.codehunters.commons.password;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Length and composition rules for passwords. Whitespace is never allowed. Lengths are counted in
 * code points.
 *
 * @param minLength the minimum length, at least 1
 * @param maxLength the maximum length, at least {@code minLength}
 * @param required the character classes that must each appear at least once
 */
public record PasswordPolicy(int minLength, int maxLength, Set<CharacterClass> required) {

  /** At least 12 and at most 128 code points, with every character class. */
  public static final PasswordPolicy DEFAULT =
      new PasswordPolicy(12, 128, EnumSet.allOf(CharacterClass.class));

  /**
   * Validates the bounds and copies the required classes.
   *
   * @param minLength the minimum length
   * @param maxLength the maximum length
   * @param required the required character classes
   */
  public PasswordPolicy {
    if (minLength < 1 || maxLength < minLength) {
      throw new IllegalArgumentException("lengths must satisfy 1 <= minLength <= maxLength");
    }
    if (required == null) {
      throw new IllegalArgumentException("required must not be null");
    }
    var copy = EnumSet.noneOf(CharacterClass.class);
    copy.addAll(required);
    required = Collections.unmodifiableSet(copy);
  }

  /**
   * Checks a password against every rule and reports all the broken ones.
   *
   * @param password the password; a {@code char[]} can be passed through {@code CharBuffer.wrap}
   * @return the result, which never contains the password
   * @throws IllegalArgumentException if {@code password} is null
   */
  public PasswordCheck check(CharSequence password) {
    if (password == null) {
      throw new IllegalArgumentException("password must not be null");
    }
    var violations = EnumSet.noneOf(PasswordViolation.class);
    addLengthViolations(password, violations);
    if (password.codePoints().anyMatch(Character::isWhitespace)) {
      violations.add(PasswordViolation.CONTAINS_WHITESPACE);
    }
    required.stream()
        .filter(c -> !c.isPresentIn(password))
        .forEach(c -> violations.add(c.whenMissing()));
    return new PasswordCheck(violations);
  }

  private void addLengthViolations(CharSequence password, Set<PasswordViolation> violations) {
    long length = password.codePoints().count();
    if (length < minLength) {
      violations.add(PasswordViolation.TOO_SHORT);
    }
    if (length > maxLength) {
      violations.add(PasswordViolation.TOO_LONG);
    }
  }
}
