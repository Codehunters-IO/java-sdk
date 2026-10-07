package io.codehunters.commons.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TextNormalizerTest {

  @ParameterizedTest
  @CsvSource({
    "Ármando Pérez, armando_perez",
    "Crème Brûlée, creme_brulee",
    "Ñandú, nandu",
    "İstanbul, istanbul"
  })
  void should_strip_diacritics_and_lower_case(String text, String expected) {
    assertThat(TextNormalizer.simplify(text)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "Łódź, lodz",
    "Straße, strasse",
    "Ærø, aero",
    "Œuvre, oeuvre",
    "Þór, thor",
    "Ðorđe, dorde"
  })
  void should_replace_letters_that_do_not_decompose(String text, String expected) {
    assertThat(TextNormalizer.simplify(text)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '`',
      value = {"O'Brien|obrien", "v1.2.3|v123", "say \"hi\"|say_hi"})
  void should_drop_dots_and_quotes(String text, String expected) {
    assertThat(TextNormalizer.simplify(text)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      quoteCharacter = '`',
      value = {
        "a/b\\c:d*e?f|a_b_c_d_e_f",
        "`  hello   world  `|hello_world",
        "___x___|x",
        "a - b|a_b"
      })
  void should_turn_other_symbols_and_spaces_into_single_underscores(String text, String expected) {
    assertThat(TextNormalizer.simplify(text)).isEqualTo(expected);
  }

  @Test
  void should_keep_letters_of_any_script() {
    assertThat(TextNormalizer.simplify("日本語 テキスト")).isEqualTo("日本語_テキスト");
  }

  @ParameterizedTest
  @CsvSource({"Йод, йод", "Ёж, ёж", "Άθηνα, άθηνα", "Ελλάδα, ελλάδα"})
  void should_keep_diacritics_of_letters_outside_the_latin_script(String text, String expected) {
    assertThat(TextNormalizer.simplify(text)).isEqualTo(expected);
  }

  @Test
  void should_not_merge_distinct_cyrillic_letters() {
    assertThat(TextNormalizer.simplify("йод")).isNotEqualTo(TextNormalizer.simplify("иод"));
  }

  @ParameterizedTest
  @CsvSource({"ΟΔΟΣ, οδοσ", "οδος, οδοσ"})
  void should_fold_greek_final_sigma_into_sigma(String text, String expected) {
    assertThat(TextNormalizer.simplify(text)).isEqualTo(expected);
  }

  @Test
  void should_keep_marks_that_are_not_latin_diacritics() {
    assertThat(TextNormalizer.simplify("データ")).isEqualTo("データ");
  }

  @Test
  void should_return_empty_when_nothing_survives() {
    assertThat(TextNormalizer.simplify("!!! ...")).isEmpty();
  }

  @Test
  void should_return_empty_when_text_is_empty() {
    assertThat(TextNormalizer.simplify("")).isEmpty();
  }

  @Test
  void should_reject_null_text() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> TextNormalizer.simplify(null))
        .withMessage("text must not be null");
  }
}
