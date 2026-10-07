package io.codehunters.commons.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PasswordPolicyTest {

  private static final String VALID = "Abcdefgh123!";

  @Test
  void should_accept_password_meeting_every_default_rule() {
    assertThat(PasswordPolicy.DEFAULT.check(VALID).isValid()).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "ABCDEFGH123!, MISSING_LOWER",
    "abcdefgh123!, MISSING_UPPER",
    "Abcdefghijk!, MISSING_DIGIT",
    "Abcdefgh1234, MISSING_SPECIAL"
  })
  void should_report_the_missing_character_class(String password, PasswordViolation expected) {
    assertThat(PasswordPolicy.DEFAULT.check(password).violations()).containsExactly(expected);
  }

  @Test
  void should_report_too_short_when_below_minimum() {
    assertThat(PasswordPolicy.DEFAULT.check("Ab1!").violations())
        .containsExactly(PasswordViolation.TOO_SHORT);
  }

  @Test
  void should_report_too_long_when_above_maximum() {
    var policy = new PasswordPolicy(4, 6, Set.of());

    assertThat(policy.check("abcdefg").violations()).containsExactly(PasswordViolation.TOO_LONG);
  }

  @Test
  void should_report_whitespace() {
    assertThat(PasswordPolicy.DEFAULT.check("Abcd efgh123!").violations())
        .containsExactly(PasswordViolation.CONTAINS_WHITESPACE);
  }

  @Test
  void should_report_every_violation_at_once() {
    assertThat(PasswordPolicy.DEFAULT.check("ab c").violations())
        .containsExactly(
            PasswordViolation.TOO_SHORT,
            PasswordViolation.CONTAINS_WHITESPACE,
            PasswordViolation.MISSING_UPPER,
            PasswordViolation.MISSING_DIGIT,
            PasswordViolation.MISSING_SPECIAL);
  }

  @Test
  void should_count_code_points_when_measuring_length() {
    var policy = new PasswordPolicy(3, 3, Set.of());

    assertThat(policy.check("a😀b").isValid()).isTrue();
  }

  @Test
  void should_require_only_the_configured_classes() {
    var policy = new PasswordPolicy(4, 8, EnumSet.of(CharacterClass.DIGIT));

    assertThat(policy.check("1234").isValid()).isTrue();
  }

  @Test
  void should_never_show_the_password_in_the_result() {
    assertThat(PasswordPolicy.DEFAULT.check("hunter2").toString()).doesNotContain("hunter2");
  }

  @Test
  void should_reject_null_password() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> PasswordPolicy.DEFAULT.check(null))
        .withMessage("password must not be null");
  }

  @Test
  void should_reject_minimum_below_one() {
    assertThatIllegalArgumentException().isThrownBy(() -> new PasswordPolicy(0, 8, Set.of()));
  }

  @Test
  void should_reject_maximum_below_minimum() {
    assertThatIllegalArgumentException().isThrownBy(() -> new PasswordPolicy(8, 7, Set.of()));
  }

  @Test
  void should_reject_null_required_classes() {
    assertThatIllegalArgumentException().isThrownBy(() -> new PasswordPolicy(8, 9, null));
  }

  @Test
  void should_not_be_affected_by_later_changes_to_the_given_set() {
    var required = EnumSet.of(CharacterClass.DIGIT);
    var policy = new PasswordPolicy(1, 8, required);

    required.add(CharacterClass.UPPER);

    assertThat(policy.required()).containsExactly(CharacterClass.DIGIT);
  }
}
