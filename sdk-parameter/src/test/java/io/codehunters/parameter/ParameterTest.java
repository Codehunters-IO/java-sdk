package io.codehunters.parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParameterTest {

  private static final UUID OP = UUID.randomUUID();
  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Test
  void asString_returns_value_when_type_is_STRING() {
    Parameter p = build("hello", ParameterType.STRING);
    assertThat(p.asString()).isEqualTo("hello");
  }

  @Test
  void asString_throws_when_type_is_not_STRING() {
    Parameter p = build("hello", ParameterType.INTEGER);
    assertThatThrownBy(p::asString).isInstanceOf(ParameterTypeMismatchException.class);
  }

  @Test
  void asLong_returns_parsed_value() {
    Parameter p = build(" 42 ", ParameterType.INTEGER);
    assertThat(p.asLong()).isEqualTo(42L);
  }

  @Test
  void asInteger_returns_parsed_int() {
    Parameter p = build("7", ParameterType.INTEGER);
    assertThat(p.asInteger()).isEqualTo(7);
  }

  @Test
  void asLong_throws_when_type_is_not_INTEGER() {
    Parameter p = build("42", ParameterType.STRING);
    assertThatThrownBy(p::asLong).isInstanceOf(ParameterTypeMismatchException.class);
  }

  @Test
  void asLong_throws_when_value_unparseable() {
    Parameter p = build("nope", ParameterType.INTEGER);
    assertThatThrownBy(p::asLong).isInstanceOf(ParameterTypeMismatchException.class);
  }

  @Test
  void asBigDecimal_returns_parsed_value() {
    Parameter p = build("3.14", ParameterType.DECIMAL);
    assertThat(p.asBigDecimal()).isEqualByComparingTo(new BigDecimal("3.14"));
  }

  @Test
  void asBigDecimal_throws_when_type_is_not_DECIMAL() {
    Parameter p = build("3.14", ParameterType.STRING);
    assertThatThrownBy(p::asBigDecimal).isInstanceOf(ParameterTypeMismatchException.class);
  }

  @Test
  void asBigDecimal_throws_when_value_unparseable() {
    Parameter p = build("nope", ParameterType.DECIMAL);
    assertThatThrownBy(p::asBigDecimal).isInstanceOf(ParameterTypeMismatchException.class);
  }

  @Test
  void asBoolean_returns_parsed_value() {
    assertThat(build("true", ParameterType.BOOLEAN).asBoolean()).isTrue();
    assertThat(build("FALSE", ParameterType.BOOLEAN).asBoolean()).isFalse();
  }

  @Test
  void asBoolean_throws_when_type_is_not_BOOLEAN() {
    Parameter p = build("true", ParameterType.STRING);
    assertThatThrownBy(p::asBoolean).isInstanceOf(ParameterTypeMismatchException.class);
  }

  @Test
  void should_reject_a_boolean_that_is_neither_true_nor_false() {
    Parameter p = build("yes", ParameterType.BOOLEAN);
    assertThatThrownBy(p::asBoolean).isInstanceOf(ParameterTypeMismatchException.class);
  }

  @Test
  void should_read_a_boolean_ignoring_case_and_surrounding_spaces() {
    assertThat(build(" False ", ParameterType.BOOLEAN).asBoolean()).isFalse();
  }

  @Test
  void should_report_a_mismatch_when_a_long_value_is_null() {
    Parameter p = build(null, ParameterType.INTEGER);
    assertThatThrownBy(p::asLong).isInstanceOf(ParameterTypeMismatchException.class);
  }

  @Test
  void should_report_a_mismatch_when_a_decimal_value_is_null() {
    Parameter p = build(null, ParameterType.DECIMAL);
    assertThatThrownBy(p::asBigDecimal).isInstanceOf(ParameterTypeMismatchException.class);
  }

  @Test
  void should_report_a_mismatch_when_a_boolean_value_is_null() {
    Parameter p = build(null, ParameterType.BOOLEAN);
    assertThatThrownBy(p::asBoolean).isInstanceOf(ParameterTypeMismatchException.class);
  }

  @Test
  void should_report_a_mismatch_when_the_declared_type_is_unknown() {
    Parameter p = build("hello", null);
    assertThatThrownBy(p::asString).isInstanceOf(ParameterTypeMismatchException.class);
  }

  @Test
  void should_report_a_mismatch_when_an_integer_overflows() {
    Parameter p = build("3000000000", ParameterType.INTEGER);
    assertThatThrownBy(p::asInteger).isInstanceOf(ParameterTypeMismatchException.class);
  }

  @Test
  void should_name_the_parameter_without_its_value_when_parsing_fails() {
    Parameter p = build("secret-42x", ParameterType.INTEGER);
    assertThatThrownBy(p::asLong)
        .hasMessageContaining("MAX")
        .hasMessageNotContaining("secret-42x")
        .hasNoCause();
  }

  @Test
  void value_returns_raw_JSON_string_for_consumer_parsing() {
    Parameter p = build("{\"k\":\"v\"}", ParameterType.JSON);
    assertThat(p.value()).isEqualTo("{\"k\":\"v\"}");
    assertThat(p.type()).isEqualTo(ParameterType.JSON);
  }

  private Parameter build(String value, ParameterType type) {
    return new Parameter(
        UUID.randomUUID(),
        ParameterModule.of("ORDERS"),
        "TICKETS",
        "MAX",
        value,
        type,
        true,
        "desc",
        OP,
        NOW,
        OP,
        NOW);
  }
}
