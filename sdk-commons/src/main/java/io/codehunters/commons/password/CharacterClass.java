package io.codehunters.commons.password;

import java.util.function.IntPredicate;

/** A class of characters a {@link PasswordPolicy} can require. Tests are Unicode-aware. */
public enum CharacterClass {
  /** A lower-case letter. */
  LOWER(Character::isLowerCase, PasswordViolation.MISSING_LOWER),
  /** An upper-case letter. */
  UPPER(Character::isUpperCase, PasswordViolation.MISSING_UPPER),
  /** A digit. */
  DIGIT(Character::isDigit, PasswordViolation.MISSING_DIGIT),
  /** Anything that is neither a letter, a digit nor whitespace. */
  SPECIAL(
      codePoint -> !Character.isLetterOrDigit(codePoint) && !Character.isWhitespace(codePoint),
      PasswordViolation.MISSING_SPECIAL);

  private final IntPredicate test;
  private final PasswordViolation whenMissing;

  CharacterClass(IntPredicate test, PasswordViolation whenMissing) {
    this.test = test;
    this.whenMissing = whenMissing;
  }

  boolean isPresentIn(CharSequence text) {
    return text.codePoints().anyMatch(test);
  }

  PasswordViolation whenMissing() {
    return whenMissing;
  }
}
