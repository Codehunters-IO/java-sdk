package io.codehunters.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ZoneRefTest {

  @Test
  void should_keep_the_reference_when_it_is_a_plain_value() {
    assertThat(new ZoneRef("campaign-42").value()).isEqualTo("campaign-42");
  }

  @Test
  void should_accept_the_maximum_length_when_it_has_128_characters() {
    assertThat(new ZoneRef("a".repeat(ZoneRef.MAX_LENGTH)).value()).hasSize(128);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t"})
  void should_reject_the_reference_when_it_is_blank(String value) {
    assertThatThrownBy(() -> new ZoneRef(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Zone reference must not be blank");
  }

  @Test
  void should_reject_the_reference_when_it_is_longer_than_128_characters() {
    assertThatThrownBy(() -> new ZoneRef("a".repeat(129)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Zone reference must have at most 128 characters");
  }

  @ParameterizedTest
  @ValueSource(strings = {"scope\nnext", "scope\u0000", "scope​"})
  void should_reject_the_reference_without_echoing_it_when_it_has_invisible_characters(
      String value) {
    assertThatThrownBy(() -> new ZoneRef(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Zone reference must not contain control or format characters");
  }
}
