package io.codehunters.commons.password;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Length and composition rules for passwords. Whitespace (including no-break spaces) and invisible
 * characters (control, format, unpaired surrogate, private-use, unassigned) are never allowed.
 * Lengths are counted in code points.
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
   * Checks a password against every rule and reports all the broken ones. Input longer than twice
   * {@code maxLength} in UTF-16 units is necessarily too long, so it is reported as {@link
   * PasswordViolation#TOO_LONG} alone without scanning it.
   *
   * @param password the password; a {@code char[]} can be passed through {@code CharBuffer.wrap}
   * @return the result, which never contains the password
   * @throws IllegalArgumentException if {@code password} is null
   */
  public PasswordCheck check(CharSequence password) {
    if (password == null) {
      throw new IllegalArgumentException("password must not be null");
    }
    if (password.length() > 2L * maxLength) {
      return new PasswordCheck(EnumSet.of(PasswordViolation.TOO_LONG));
    }
    var violations = EnumSet.noneOf(PasswordViolation.class);
    addLengthViolations(password, violations);
    addCharacterViolations(password, violations);
    required.stream()
        .filter(c -> !c.isPresentIn(password))
        .forEach(c -> violations.add(c.whenMissing()));
    return new PasswordCheck(violations);
  }

  private static void addCharacterViolations(
      CharSequence password, Set<PasswordViolation> violations) {
    if (password.codePoints().anyMatch(PasswordPolicy::isSpace)) {
      violations.add(PasswordViolation.CONTAINS_WHITESPACE);
    }
    if (password.codePoints().anyMatch(PasswordPolicy::isInvisible)) {
      violations.add(PasswordViolation.CONTAINS_INVISIBLE);
    }
  }

  private static boolean isSpace(int codePoint) {
    return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
  }

  private static boolean isInvisible(int codePoint) {
    int type = Character.getType(codePoint);
    boolean invisible =
        type == Character.CONTROL
            || type == Character.FORMAT
            || type == Character.SURROGATE
            || type == Character.PRIVATE_USE
            || type == Character.UNASSIGNED;
    return invisible && !isSpace(codePoint);
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
