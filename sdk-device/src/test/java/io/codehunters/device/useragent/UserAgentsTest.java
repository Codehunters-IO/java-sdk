package io.codehunters.device.useragent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class UserAgentsTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      nullValues = "NULL",
      emptyValue = "EMPTY",
      textBlock =
          """
          Mozilla/5.0         | 64 | Mozilla/5.0
          '  Mozilla/5.0  '   | 64 | Mozilla/5.0
          Mozilla/5.0 (X11)   | 7  | Mozilla
          NULL                | 64 | EMPTY
          EMPTY               | 64 | EMPTY
          """)
  void should_trim_and_truncate_the_header(String raw, int maxLength, String expected) {
    assertThat(UserAgents.sanitize(raw, maxLength)).isEqualTo(expected);
  }

  @Test
  void should_replace_control_characters_with_spaces() {
    assertThat(UserAgents.sanitize("a\u0000b\tc\u007fd\u0085e", 64)).isEqualTo("a b c d e");
  }

  @Test
  void should_not_split_a_surrogate_pair_when_truncating() {
    assertThat(UserAgents.sanitize("ab😀c", 3)).isEqualTo("ab");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void should_reject_a_max_length_below_one(int maxLength) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> UserAgents.sanitize("Mozilla/5.0", maxLength))
        .withMessage("max length must be at least 1");
  }
}
