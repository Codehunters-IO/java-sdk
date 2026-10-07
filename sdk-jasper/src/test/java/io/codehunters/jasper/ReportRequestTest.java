package io.codehunters.jasper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReportRequestTest {

  private static final String TEMPLATE = "orders.jrxml";

  @Test
  void should_start_with_no_parameters_and_no_rows() {
    var request = ReportRequest.of(TEMPLATE, ReportFormat.PDF);

    assertThat(request.parameters()).isEmpty();
    assertThat(request.data()).isEqualTo(ReportData.empty());
  }

  @Test
  void should_copy_the_parameters_so_later_changes_do_not_leak_in() {
    var parameters = new HashMap<String, Object>(Map.of("title", "Orders"));
    var request = ReportRequest.of(TEMPLATE, ReportFormat.PDF).withParameters(parameters);

    parameters.put("title", "Changed");

    assertThat(request.parameters()).containsEntry("title", "Orders");
  }

  @Test
  void should_keep_parameters_whose_value_is_null() {
    var parameters = new HashMap<String, Object>();
    parameters.put("subtitle", null);

    var request = ReportRequest.of(TEMPLATE, ReportFormat.PDF).withParameters(parameters);

    assertThat(request.parameters()).containsEntry("subtitle", null);
  }

  @Test
  void should_expose_unmodifiable_parameters() {
    var request = ReportRequest.of(TEMPLATE, ReportFormat.PDF).withParameters(Map.of("a", 1));

    assertThatThrowsUnsupported(request.parameters());
  }

  @Test
  void should_keep_the_rows_it_was_given() {
    var request =
        ReportRequest.of(TEMPLATE, ReportFormat.CSV).withData(ReportData.beans(List.of("x")));

    assertThat(request.data()).isEqualTo(ReportData.beans(List.of("x")));
  }

  @Test
  void should_reject_a_template_outside_the_classpath_without_repeating_it() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ReportRequest.of("../../etc/passwd.jrxml", ReportFormat.PDF))
        .withMessage(
            "template must be a relative classpath name ending in .jasper or .jrxml, made of"
                + " letters, digits, '.', '_', '-' and '/'");
  }

  @Test
  void should_reject_a_missing_format() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ReportRequest.of(TEMPLATE, null))
        .withMessage("format must not be null");
  }

  @Test
  void should_reject_missing_parameters() {
    var request = ReportRequest.of(TEMPLATE, ReportFormat.PDF);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> request.withParameters(null))
        .withMessage("parameters must not be null");
  }

  @Test
  void should_reject_missing_data() {
    var request = ReportRequest.of(TEMPLATE, ReportFormat.PDF);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> request.withData(null))
        .withMessage("data must not be null");
  }

  @Test
  void should_reject_a_blank_parameter_name() {
    var request = ReportRequest.of(TEMPLATE, ReportFormat.PDF);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> request.withParameters(Map.of(" ", 1)))
        .withMessage("parameter names must not be null or blank");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "REPORT_VIRTUALIZER",
        "REPORT_CLASS_LOADER",
        "REPORT_CONNECTION",
        "REPORT_DATA_SOURCE",
        "REPORT_CONTEXT",
        "REPORT_URL_HANDLER_FACTORY",
        "REPORT_FILE_RESOLVER",
        "REPORT_PARAMETERS_MAP",
        "JASPER_REPORTS_CONTEXT"
      })
  void should_reject_a_parameter_the_sdk_manages(String name) {
    var request = ReportRequest.of(TEMPLATE, ReportFormat.PDF);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> request.withParameters(Map.of(name, 1)))
        .withMessage("parameter " + name + " is managed by the SDK");
  }

  @Test
  void should_accept_the_locale_and_time_zone_parameters() {
    var request =
        ReportRequest.of(TEMPLATE, ReportFormat.PDF)
            .withParameters(Map.of("REPORT_LOCALE", java.util.Locale.ROOT));

    assertThat(request.parameters()).containsKey("REPORT_LOCALE");
  }

  @Test
  void should_show_parameter_names_but_never_their_values() {
    var request =
        ReportRequest.of(TEMPLATE, ReportFormat.PDF)
            .withParameters(Map.of("customer", "Ana Secret"))
            .withData(ReportData.beans(List.of("row-secret")));

    assertThat(request.toString())
        .isEqualTo(
            "ReportRequest[template=orders.jrxml, format=PDF, parameters=[customer], data=1 row]")
        .doesNotContain("Secret", "row-secret");
  }

  private static void assertThatThrowsUnsupported(Map<String, Object> parameters) {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> parameters.put("b", 2))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  static Stream<Object> locationsThatBypassTheClasspath() throws Exception {
    return Stream.of(
        URI.create("https://example.com/logo.png").toURL(),
        URI.create("file:///etc/hosts"),
        new File("/etc/hosts"),
        Path.of("/etc/hosts"),
        new ByteArrayInputStream(new byte[0]),
        new StringReader(""),
        List.of(new File("/etc/hosts")),
        new Object[] {Path.of("/etc/hosts")},
        Map.of("logo", URI.create("file:///etc/hosts")));
  }

  @ParameterizedTest
  @MethodSource("locationsThatBypassTheClasspath")
  void should_reject_a_parameter_value_that_loads_outside_the_classpath(Object value) {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                ReportRequest.of(TEMPLATE, ReportFormat.PDF).withParameters(Map.of("logo", value)))
        .withMessage(
            "parameter logo holds a URL, URI, File, Path, stream or reader; pass a classpath"
                + " location as a String");
  }

  @Test
  void should_accept_image_bytes_as_a_parameter_value() {
    var request =
        ReportRequest.of(TEMPLATE, ReportFormat.PDF).withParameters(Map.of("logo", new byte[1]));

    assertThat(request.parameters()).containsKey("logo");
  }
}
