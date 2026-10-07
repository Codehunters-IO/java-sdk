package io.codehunters.commons.masking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MaskingTest {

  private static final String SECRET_VALUE = "s3cr3t-value";

  @ParameterizedTest
  @CsvSource({
    "1234567890, 2, 2, 12******90",
    "1234567890, 0, 4, ******7890",
    "1234567890, 3, 0, 123*******",
    "1234567890, 0, 0, **********"
  })
  void should_keep_requested_edges_when_text_is_longer_than_them(
      String text, int first, int last, String expected) {
    assertThat(Masking.keep(text, first, last)).isEqualTo(expected);
  }

  @Test
  void should_mask_everything_when_kept_edges_would_reveal_the_whole_text() {
    assertThat(Masking.keep("abcd", 2, 2)).isEqualTo("****");
  }

  @Test
  void should_use_the_given_mask_character() {
    assertThat(Masking.keep("abcdef", 1, 1, '#')).isEqualTo("a####f");
  }

  @Test
  void should_return_empty_when_text_is_empty() {
    assertThat(Masking.keep("", 1, 1)).isEmpty();
  }

  @Test
  void should_count_code_points_not_chars_when_text_has_surrogate_pairs() {
    assertThat(Masking.keep("😀abc😁", 1, 1)).isEqualTo("😀***😁");
  }

  @Test
  void should_keep_only_first_characters() {
    assertThat(Masking.keepFirst("abcdef", 2)).isEqualTo("ab****");
  }

  @Test
  void should_keep_only_last_characters() {
    assertThat(Masking.keepLast("abcdef", 2)).isEqualTo("****ef");
  }

  @Test
  void should_reject_negative_counts() {
    assertThatIllegalArgumentException().isThrownBy(() -> Masking.keep("abc", -1, 0));
  }

  @Test
  void should_reject_null_text_without_echoing_anything() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Masking.keep(null, 1, 1))
        .withMessage("text must not be null");
  }

  @Test
  void should_not_echo_the_value_when_counts_are_invalid() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Masking.keep(SECRET_VALUE, -2, 0))
        .withMessageNotContaining(SECRET_VALUE);
  }

  @ParameterizedTest
  @CsvSource({
    "john.doe@example.com, j*******@example.com",
    "a@example.com, *@example.com",
    "Ana@Mail.co, A**@Mail.co"
  })
  void should_keep_first_letter_and_domain_when_masking_email(String email, String expected) {
    assertThat(Masking.email(email)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-an-email", "@example.com", "user@", "a@b@c"})
  void should_mask_whole_value_when_email_is_malformed(String value) {
    assertThat(Masking.email(value)).isEqualTo("*".repeat(value.length()));
  }

  @ParameterizedTest
  @CsvSource({
    "4111111111111111, ************1111",
    "4111 1111 1111 1111, ************1111",
    "4111-1111-1111-1111, ************1111",
    "411111111111, ********1111"
  })
  void should_keep_last_four_digits_when_masking_card_number(String pan, String expected) {
    assertThat(Masking.pan(pan)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"41111111111", "41111111111111111111", "4111abcd11111111", ""})
  void should_reject_values_that_are_not_card_numbers(String value) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> Masking.pan(value))
        .withMessage("value is not a card number of 12 to 19 digits");
  }
}
