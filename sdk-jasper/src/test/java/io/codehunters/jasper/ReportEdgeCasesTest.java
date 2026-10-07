package io.codehunters.jasper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ObjectOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReportEdgeCasesTest {

  record Customer(String name) {}

  record Order(String name, Customer customer) {}

  private final JasperReportRenderer renderer = new JasperReportRenderer();

  @TempDir Path folder;

  @ParameterizedTest
  @CsvSource({
    "PDF, application/pdf, pdf",
    "XLSX, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, xlsx",
    "CSV, text/csv; charset=UTF-8, csv"
  })
  void should_describe_each_format_for_a_response(
      ReportFormat format, String mediaType, String extension) {
    assertThat(format.mediaType()).isEqualTo(mediaType);
    assertThat(format.extension()).isEqualTo(extension);
  }

  @Test
  void should_hand_the_whole_row_to_the_this_field() {
    var request =
        ReportRequest.of("this.jrxml", ReportFormat.CSV)
            .withData(ReportData.beans(List.of(new Customer("Zoe"))));

    assertThat(new String(renderer.toBytes(request), StandardCharsets.UTF_8))
        .contains("Customer[name=Zoe]");
  }

  @Test
  void should_read_a_null_for_a_dotted_field_under_a_null_component() {
    var request =
        ReportRequest.of("customers.jrxml", ReportFormat.CSV)
            .withData(ReportData.beans(List.of(new Order("Eva", null))));

    assertThat(new String(renderer.toBytes(request), StandardCharsets.UTF_8)).contains("null");
  }

  @Test
  void should_fail_when_a_precompiled_template_is_corrupt() {
    assertThatThrownBy(() -> renderer.toBytes(ReportRequest.of("corrupt.jasper", ReportFormat.PDF)))
        .isInstanceOf(ReportException.class)
        .hasMessage("template corrupt.jasper could not be loaded");
  }

  @Test
  void should_fail_when_a_precompiled_template_holds_something_else() throws Exception {
    Path reports = Files.createDirectories(folder.resolve("reports"));
    try (var out = new ObjectOutputStream(Files.newOutputStream(reports.resolve("text.jasper")))) {
      out.writeObject("not a report");
    }
    var loader = new URLClassLoader(new URL[] {folder.toUri().toURL()}, null);
    var other = new JasperReportRenderer(ReportSettings.DEFAULT, loader);

    assertThatThrownBy(() -> other.toBytes(ReportRequest.of("text.jasper", ReportFormat.PDF)))
        .isInstanceOf(ReportException.class)
        .hasMessage("template text.jasper is not a compiled report");
  }

  @Test
  void should_compile_on_every_render_when_the_cache_is_off() {
    var uncached =
        new JasperReportRenderer(new ReportSettings("reports", 0, 1 << 20, Virtualization.OFF));
    var request = ReportRequest.of("this.jrxml", ReportFormat.CSV);

    uncached.toBytes(request);

    assertThat(uncached.toBytes(request)).isNotEmpty();
  }

  @Test
  void should_reject_missing_settings_or_class_loader() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JasperReportRenderer(null, getClass().getClassLoader()))
        .withMessage("settings and loader must not be null");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JasperReportRenderer(ReportSettings.DEFAULT, null))
        .withMessage("settings and loader must not be null");
  }
}
