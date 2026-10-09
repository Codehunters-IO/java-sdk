package io.codehunters.parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ParameterModuleTest {

  @Test
  void should_expose_the_known_modules_as_constants() {
    assertThat(ParameterModule.RAFFLES.code()).isEqualTo("RAFFLES");
  }

  @Test
  void should_accept_a_module_the_sdk_does_not_know() {
    assertThat(ParameterModule.of("LOYALTY").code()).isEqualTo("LOYALTY");
  }

  @Test
  void should_equal_the_constant_with_the_same_code() {
    assertThat(ParameterModule.of("RAFFLES")).isEqualTo(ParameterModule.RAFFLES);
  }

  @Test
  void should_print_its_code() {
    assertThat(ParameterModule.PAYMENT).hasToString("PAYMENT");
  }

  @Test
  void should_reject_a_blank_code() {
    assertThatThrownBy(() -> ParameterModule.of(" ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_reject_a_null_code() {
    assertThatThrownBy(() -> ParameterModule.of(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_return_the_constant_instance_for_a_known_code() {
    assertThat(ParameterModule.of("AUTH")).isSameAs(ParameterModule.AUTH);
  }

  @Test
  void should_trim_the_code_before_matching_a_constant() {
    assertThat(ParameterModule.of(" AUTH ")).isSameAs(ParameterModule.AUTH);
  }

  @Test
  void should_trim_the_code_given_to_the_constructor() {
    assertThat(new ParameterModule(" LOYALTY ").code()).isEqualTo("LOYALTY");
  }

  @Test
  void should_match_codes_case_sensitively() {
    assertThat(ParameterModule.of("auth")).isNotEqualTo(ParameterModule.AUTH);
  }
}
