package io.codehunters.commons.password;

import java.util.Set;
import java.util.function.IntPredicate;

/** A class of characters a {@link PasswordPolicy} can require. Tests are Unicode-aware. */
public enum CharacterClass {
  /** A lower-case letter. */
  LOWER(Character::isLowerCase, PasswordViolation.MISSING_LOWER),
  /** An upper-case letter. */
  UPPER(Character::isUpperCase, PasswordViolation.MISSING_UPPER),
  /** A digit. */
  DIGIT(Character::isDigit, PasswordViolation.MISSING_DIGIT),
  /**
   * A visible punctuation mark or symbol of any script, such as {@code !}, {@code €} or an emoji.
   * Spaces, invisible characters and combining marks never count.
   */
  SPECIAL(CharacterClass::isPunctuationOrSymbol, PasswordViolation.MISSING_SPECIAL);

  private static final Set<Integer> PUNCTUATION_AND_SYMBOLS =
      Set.of(
          (int) Character.CONNECTOR_PUNCTUATION,
          (int) Character.DASH_PUNCTUATION,
          (int) Character.START_PUNCTUATION,
          (int) Character.END_PUNCTUATION,
          (int) Character.INITIAL_QUOTE_PUNCTUATION,
          (int) Character.FINAL_QUOTE_PUNCTUATION,
          (int) Character.OTHER_PUNCTUATION,
          (int) Character.MATH_SYMBOL,
          (int) Character.CURRENCY_SYMBOL,
          (int) Character.MODIFIER_SYMBOL,
          (int) Character.OTHER_SYMBOL);

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

  private static boolean isPunctuationOrSymbol(int codePoint) {
    return PUNCTUATION_AND_SYMBOLS.contains(Character.getType(codePoint));
  }
}
