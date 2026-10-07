package io.codehunters.commons.password;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Outcome of {@link PasswordPolicy#check(CharSequence)}. It holds the broken rules only, never the
 * password, so it is safe to log.
 *
 * @param violations the broken rules, in declaration order; empty when the password is valid
 */
public record PasswordCheck(Set<PasswordViolation> violations) {

  /**
   * Copies the violations into an unmodifiable, ordered set.
   *
   * @param violations the broken rules
   * @throws IllegalArgumentException if {@code violations} is null
   */
  public PasswordCheck {
    if (violations == null) {
      throw new IllegalArgumentException("violations must not be null");
    }
    var ordered = EnumSet.noneOf(PasswordViolation.class);
    ordered.addAll(violations);
    violations = Collections.unmodifiableSet(ordered);
  }

  /**
   * Tells whether the password broke no rule.
   *
   * @return {@code true} when there are no violations
   */
  public boolean isValid() {
    return violations.isEmpty();
  }
}
