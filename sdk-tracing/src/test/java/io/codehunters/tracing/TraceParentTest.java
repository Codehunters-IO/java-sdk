package io.codehunters.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TraceParentTest {

  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String SPAN_ID = "00f067aa0ba902b7";
  private static final String HEADER = "00-" + TRACE_ID + "-" + SPAN_ID + "-01";

  @Test
  void should_parse_valid_header() {
    assertThat(TraceParent.parse(HEADER)).contains(new TraceParent(TRACE_ID, SPAN_ID, true));
  }

  @Test
  void should_read_unsampled_flag() {
    assertThat(TraceParent.parse("00-" + TRACE_ID + "-" + SPAN_ID + "-00"))
        .hasValueSatisfying(parent -> assertThat(parent.sampled()).isFalse());
  }

  @Test
  void should_format_back_to_the_same_header() {
    assertThat(TraceParent.parse(HEADER).map(TraceParent::format)).contains(HEADER);
  }

  @Test
  void should_accept_future_version_with_extra_fields() {
    assertThat(TraceParent.parse("01-" + TRACE_ID + "-" + SPAN_ID + "-01-extra")).isPresent();
  }

  @Test
  void should_reject_version_ff() {
    assertThat(TraceParent.parse("ff-" + TRACE_ID + "-" + SPAN_ID + "-01")).isEmpty();
  }

  @Test
  void should_reject_extra_fields_in_version_00() {
    assertThat(TraceParent.parse(HEADER + "-extra")).isEmpty();
  }

  @Test
  void should_reject_all_zero_trace_id() {
    assertThat(TraceParent.parse("00-" + "0".repeat(32) + "-" + SPAN_ID + "-01")).isEmpty();
  }

  @Test
  void should_reject_all_zero_span_id() {
    assertThat(TraceParent.parse("00-" + TRACE_ID + "-" + "0".repeat(16) + "-01")).isEmpty();
  }

  @Test
  void should_reject_upper_case_hex() {
    assertThat(TraceParent.parse(HEADER.toUpperCase(java.util.Locale.ROOT))).isEmpty();
  }

  @Test
  void should_reject_uuid_shaped_trace_id() {
    assertThat(TraceParent.ofTraceId("4bf92f35-77b3-4da6-a3ce-929d0e0e4736")).isEmpty();
  }

  @Test
  void should_reject_missing_header() {
    assertThat(TraceParent.parse(null)).isEmpty();
  }

  @Test
  void should_keep_trace_and_flags_when_starting_child_span() {
    TraceParent parent = new TraceParent(TRACE_ID, SPAN_ID, false);

    TraceParent child = parent.child();

    assertThat(child.traceId()).isEqualTo(TRACE_ID);
    assertThat(child.sampled()).isFalse();
    assertThat(child.spanId()).isNotEqualTo(SPAN_ID).matches("[0-9a-f]{16}");
  }

  @Test
  void should_generate_valid_sampled_context() {
    TraceParent generated = TraceParent.generate();

    assertThat(TraceParent.parse(generated.format())).contains(generated);
    assertThat(generated.sampled()).isTrue();
  }
}
