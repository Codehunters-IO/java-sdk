package io.codehunters.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class W3cTraceParentTest {

  private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String SPAN = "00f067aa0ba902b7";
  private static final String VALID = "00-" + TRACE + "-" + SPAN + "-01";
  private static final String ZERO_TRACE = "00000000000000000000000000000000";
  private static final String ZERO_SPAN = "0000000000000000";

  @Test
  void should_parse_fields_when_value_is_a_version_00_traceparent() {
    W3cTraceParent parsed = W3cTraceParent.parse(VALID).orElseThrow();

    assertThat(parsed.version()).isEqualTo("00");
    assertThat(parsed.traceId()).isEqualTo(TRACE);
    assertThat(parsed.spanId()).isEqualTo(SPAN);
    assertThat(parsed.flags()).isEqualTo("01");
  }

  @Test
  void should_format_the_parsed_value_when_value_is_valid() {
    assertThat(W3cTraceParent.parse(VALID).orElseThrow().value()).isEqualTo(VALID);
  }

  @Test
  void should_be_sampled_when_flags_have_the_sampled_bit() {
    assertThat(W3cTraceParent.parse("00-" + TRACE + "-" + SPAN + "-03").orElseThrow().sampled())
        .isTrue();
  }

  @Test
  void should_not_be_sampled_when_flags_lack_the_sampled_bit() {
    assertThat(W3cTraceParent.parse("00-" + TRACE + "-" + SPAN + "-00").orElseThrow().sampled())
        .isFalse();
  }

  @Test
  void should_accept_value_when_a_future_version_has_exactly_the_four_fields() {
    assertThat(W3cTraceParent.isValid("01-" + TRACE + "-" + SPAN + "-01")).isTrue();
  }

  @Test
  void should_reject_value_when_null() {
    assertThat(W3cTraceParent.parse(null)).isEmpty();
  }

  @Test
  void should_reject_value_when_longer_than_55_characters() {
    assertThat(W3cTraceParent.isValid(VALID + "-extra")).isFalse();
  }

  @Test
  void should_reject_value_when_shorter_than_55_characters() {
    assertThat(W3cTraceParent.isValid("00-" + TRACE + "-" + SPAN + "-1")).isFalse();
  }

  @Test
  void should_reject_value_when_malformed() {
    assertThat(W3cTraceParent.isValid("00-not-a-trace-01")).isFalse();
  }

  @Test
  void should_reject_value_when_separators_are_misplaced() {
    assertThat(W3cTraceParent.isValid("00_" + TRACE + "_" + SPAN + "_01")).isFalse();
  }

  @Test
  void should_reject_value_when_version_is_ff() {
    assertThat(W3cTraceParent.isValid("ff-" + TRACE + "-" + SPAN + "-01")).isFalse();
  }

  @Test
  void should_reject_value_when_hex_is_upper_case() {
    assertThat(W3cTraceParent.isValid("00-" + TRACE.toUpperCase() + "-" + SPAN + "-01")).isFalse();
  }

  @Test
  void should_reject_value_when_trace_id_is_all_zeros() {
    assertThat(W3cTraceParent.isValid("00-" + ZERO_TRACE + "-" + SPAN + "-01")).isFalse();
  }

  @Test
  void should_reject_value_when_span_id_is_all_zeros() {
    assertThat(W3cTraceParent.isValid("00-" + TRACE + "-" + ZERO_SPAN + "-01")).isFalse();
  }

  @Test
  void should_return_valid_value_when_sanitizing_a_valid_traceparent() {
    assertThat(W3cTraceParent.validOrNull(VALID)).isEqualTo(VALID);
  }

  @Test
  void should_return_null_when_sanitizing_an_invalid_traceparent() {
    assertThat(W3cTraceParent.validOrNull(VALID + "-extra")).isNull();
  }

  @Test
  void should_throw_when_constructed_with_invalid_fields() {
    assertThatThrownBy(() -> new W3cTraceParent("ff", TRACE, SPAN, "01"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
