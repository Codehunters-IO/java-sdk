package io.codehunters.commons.password;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordGeneratorTest {

  private static final RandomGenerator ALWAYS_ZERO = () -> 0L;

  private final PasswordGenerator generator = new PasswordGenerator();

  @Test
  void should_generate_default_length_password_that_meets_the_default_policy() {
    String password = generator.generate();

    assertThat(password).hasSize(PasswordGenerator.DEFAULT_LENGTH);
    assertThat(PasswordPolicy.DEFAULT.check(password).isValid()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(ints = {PasswordGenerator.MIN_LENGTH, PasswordGenerator.MAX_LENGTH})
  void should_generate_requested_length_when_within_bounds(int length) {
    assertThat(generator.generate(length)).hasSize(length);
  }

  @ParameterizedTest
  @ValueSource(ints = {PasswordGenerator.MIN_LENGTH - 1, PasswordGenerator.MAX_LENGTH + 1})
  void should_reject_length_out_of_bounds(int length) {
    assertThatIllegalArgumentException().isThrownBy(() -> generator.generate(length));
  }

  @Test
  void should_cover_every_class_even_when_randomness_is_degenerate() {
    String password = new PasswordGenerator(ALWAYS_ZERO).generate(PasswordGenerator.MIN_LENGTH);

    assertThat(PasswordPolicy.DEFAULT.check(password).isValid()).isTrue();
  }

  @Test
  void should_avoid_characters_that_are_easy_to_confuse() {
    String password = generator.generate(PasswordGenerator.MAX_LENGTH);

    assertThat(password).doesNotContainPattern("[ilo01ILO]");
  }

  @Test
  void should_generate_numeric_code_of_requested_length() {
    assertThat(generator.numeric(6)).matches("\\d{6}");
  }

  @ParameterizedTest
  @ValueSource(ints = {PasswordGenerator.MIN_NUMERIC_LENGTH - 1, PasswordGenerator.MAX_LENGTH + 1})
  void should_reject_numeric_length_out_of_bounds(int length) {
    assertThatIllegalArgumentException().isThrownBy(() -> generator.numeric(length));
  }

  @Test
  void should_reject_null_random_generator() {
    assertThatIllegalArgumentException().isThrownBy(() -> new PasswordGenerator(null));
  }
}
