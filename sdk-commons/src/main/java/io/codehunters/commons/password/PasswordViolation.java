package io.codehunters.commons.password;

/** A rule of a {@link PasswordPolicy} that a password breaks. */
public enum PasswordViolation {
  /** Fewer code points than the policy minimum. */
  TOO_SHORT,
  /** More code points than the policy maximum. */
  TOO_LONG,
  /** Contains a whitespace character. */
  CONTAINS_WHITESPACE,
  /** Contains a control, format, unpaired surrogate, private-use or unassigned character. */
  CONTAINS_INVISIBLE,
  /** No lower-case letter. */
  MISSING_LOWER,
  /** No upper-case letter. */
  MISSING_UPPER,
  /** No digit. */
  MISSING_DIGIT,
  /** No character that is neither a letter, a digit nor whitespace. */
  MISSING_SPECIAL
}
