package io.codehunters.parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ParameterModuleTest {

  @Test
  void should_accept_any_module_code() {
    assertThat(ParameterModule.of("LOYALTY").code()).isEqualTo("LOYALTY");
  }

  @Test
  void should_equal_another_module_with_the_same_code() {
    assertThat(ParameterModule.of("ORDERS")).isEqualTo(new ParameterModule("ORDERS"));
  }

  @Test
  void should_ship_no_predefined_module_constants() {
    assertThat(Arrays.stream(ParameterModule.class.getDeclaredFields()))
        .noneMatch(ParameterModuleTest::isPublicConstant);
  }

  @Test
  void should_print_its_code() {
    assertThat(ParameterModule.of("BILLING")).hasToString("BILLING");
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
  void should_trim_the_code_given_to_of() {
    assertThat(ParameterModule.of(" AUTH ")).isEqualTo(ParameterModule.of("AUTH"));
  }

  @Test
  void should_trim_the_code_given_to_the_constructor() {
    assertThat(new ParameterModule(" LOYALTY ").code()).isEqualTo("LOYALTY");
  }

  @Test
  void should_match_codes_case_sensitively() {
    assertThat(ParameterModule.of("auth")).isNotEqualTo(ParameterModule.of("AUTH"));
  }

  private static boolean isPublicConstant(Field field) {
    int modifiers = field.getModifiers();
    return Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers);
  }
}
