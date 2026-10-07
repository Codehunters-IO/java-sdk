package io.codehunters.jasper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JasperReportRendererTest {

  private static final List<Order> ORDERS = List.of(new Order("Ana", 10), new Order("Luis", 25));

  private final JasperReportRenderer renderer = new JasperReportRenderer();

  @TempDir Path folder;

  @Test
  void should_render_a_pdf_with_its_parameters_and_rows() {
    byte[] pdf = renderer.toBytes(orders(ReportFormat.PDF));

    assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    assertThat(Documents.pdfText(pdf)).contains("Orders of the day", "Ana", "10", "Luis", "25");
  }

  @Test
  void should_render_an_xlsx_workbook() {
    byte[] xlsx = renderer.toBytes(orders(ReportFormat.XLSX));

    assertThat(Documents.hasZipEntry(xlsx, folder, "xl/workbook.xml")).isTrue();
  }

  @Test
  void should_render_csv_in_utf_8() {
    var request =
        orders(ReportFormat.CSV).withData(ReportData.beans(List.of(new Order("Begoña", 7))));

    String csv = new String(renderer.toBytes(request), StandardCharsets.UTF_8);

    assertThat(csv).contains("Begoña", "7");
  }

  record Customer(String name) {}

  record OrderRecord(String name, Integer amount, Customer customer) {}

  @Test
  void should_read_fields_from_record_components() {
    var request =
        orders(ReportFormat.PDF)
            .withData(ReportData.beans(List.of(new OrderRecord("Eva", 3, new Customer("ACME")))));

    assertThat(Documents.pdfText(renderer.toBytes(request))).contains("Eva", "3");
  }

  @Test
  void should_read_nested_record_components_through_a_dotted_field() {
    var request =
        ReportRequest.of("customers.jrxml", ReportFormat.CSV)
            .withData(ReportData.beans(List.of(new OrderRecord("Eva", 3, new Customer("ACME")))));

    assertThat(new String(renderer.toBytes(request), StandardCharsets.UTF_8)).contains("ACME");
  }

  @Test
  void should_fail_without_values_when_a_record_lacks_a_field() {
    var request =
        ReportRequest.of("orders.jrxml", ReportFormat.PDF)
            .withData(ReportData.beans(List.of(new Customer("SECRET-CO"))));

    assertThatThrownBy(() -> renderer.toBytes(request))
        .isInstanceOf(ReportException.class)
        .hasMessage("report orders.jrxml could not be filled")
        .rootCause()
        .hasMessageNotContaining("SECRET-CO");
  }

  @Test
  void should_fill_from_a_supplied_data_source() {
    var request =
        orders(ReportFormat.PDF)
            .withData(ReportData.source(() -> new JRBeanCollectionDataSource(ORDERS)));

    assertThat(Documents.pdfText(renderer.toBytes(request))).contains("Ana", "Luis");
  }

  @Test
  void should_stream_into_the_output_and_leave_it_open() {
    var out = new CloseTrackingStream();

    renderer.render(orders(ReportFormat.PDF), out);

    assertThat(Documents.pdfText(out.toByteArray())).contains("Ana");
    assertThat(out.closed).isFalse();
  }

  @Test
  void should_load_a_precompiled_template() throws Exception {
    Path reports = Files.createDirectories(folder.resolve("reports"));
    try (var in = getClass().getResourceAsStream("/reports/orders.jrxml");
        var out = Files.newOutputStream(reports.resolve("compiled.jasper"))) {
      JasperCompileManager.compileReportToStream(in, out);
    }
    var loader = new URLClassLoader(new URL[] {folder.toUri().toURL()}, null);
    var precompiled = new JasperReportRenderer(ReportSettings.DEFAULT, loader);
    var request =
        ReportRequest.of("compiled.jasper", ReportFormat.PDF)
            .withParameters(Map.of("title", "Precompiled"))
            .withData(ReportData.beans(ORDERS));

    assertThat(Documents.pdfText(precompiled.toBytes(request))).contains("Precompiled", "Ana");
  }

  @Test
  void should_read_templates_from_the_configured_base_folder() throws Exception {
    Path custom = Files.createDirectories(folder.resolve("custom/base"));
    Files.copy(
        Path.of(getClass().getResource("/reports/orders.jrxml").toURI()),
        custom.resolve("orders.jrxml"));
    var loader = new URLClassLoader(new URL[] {folder.toUri().toURL()}, null);
    var settings = new ReportSettings("custom/base", 4, 1024 * 1024, Virtualization.OFF);

    byte[] pdf = new JasperReportRenderer(settings, loader).toBytes(orders(ReportFormat.PDF));

    assertThat(Documents.pdfText(pdf)).contains("Ana");
  }

  @Test
  void should_fail_with_the_template_name_when_it_is_missing() {
    assertThatThrownBy(() -> renderer.toBytes(ReportRequest.of("missing.jrxml", ReportFormat.PDF)))
        .isInstanceOf(ReportException.class)
        .hasMessage("template missing.jrxml was not found under reports");
  }

  @Test
  void should_fail_when_a_template_cannot_be_compiled() {
    assertThatThrownBy(() -> renderer.toBytes(ReportRequest.of("broken.jrxml", ReportFormat.PDF)))
        .isInstanceOf(ReportException.class)
        .hasMessage("template broken.jrxml could not be compiled");
  }

  @Test
  void should_refuse_a_template_that_declares_external_entities() {
    assertThatThrownBy(() -> renderer.toBytes(ReportRequest.of("xxe.jrxml", ReportFormat.PDF)))
        .isInstanceOf(ReportException.class)
        .hasMessage("template xxe.jrxml could not be compiled");
  }

  @Test
  void should_never_put_parameter_values_in_the_message_when_filling_fails() {
    var request =
        ReportRequest.of("parse.jrxml", ReportFormat.PDF)
            .withParameters(Map.of("number", "SECRET-42"));

    assertThatThrownBy(() -> renderer.toBytes(request))
        .isInstanceOf(ReportException.class)
        .hasMessage("report parse.jrxml could not be filled");
  }

  @Test
  void should_load_images_from_the_base_folder() {
    var request =
        ReportRequest.of("image.jrxml", ReportFormat.PDF)
            .withParameters(Map.of("image", "logo.png"));

    assertThat(Documents.pdfPages(renderer.toBytes(request))).isEqualTo(1);
  }

  @Test
  void should_not_load_images_from_the_file_system() throws IOException {
    Path outside = folder.resolve("outside.png");
    Files.copy(getClass().getResourceAsStream("/reports/logo.png"), outside);
    var request =
        ReportRequest.of("image.jrxml", ReportFormat.PDF)
            .withParameters(Map.of("image", outside.toString()));

    assertThatThrownBy(() -> renderer.toBytes(request))
        .isInstanceOf(ReportException.class)
        .hasMessage("report image.jrxml could not be filled");
  }

  @Test
  void should_not_load_images_from_a_url() throws IOException {
    Path outside = folder.resolve("outside.png");
    Files.copy(getClass().getResourceAsStream("/reports/logo.png"), outside);
    var request =
        ReportRequest.of("image.jrxml", ReportFormat.PDF)
            .withParameters(Map.of("image", outside.toUri().toString()));

    assertThatThrownBy(() -> renderer.toBytes(request))
        .isInstanceOf(ReportException.class)
        .hasMessage("report image.jrxml could not be filled");
  }

  @Test
  void should_fail_once_the_document_exceeds_the_output_limit() {
    var small = new JasperReportRenderer(new ReportSettings("reports", 4, 100, Virtualization.OFF));

    assertThatThrownBy(() -> small.toBytes(orders(ReportFormat.PDF)))
        .isInstanceOf(ReportException.class)
        .hasMessage("the report exceeds the output limit of 100 bytes");
  }

  @Test
  void should_swap_pages_to_a_file_and_delete_it_afterwards() {
    var rows = Order.numbered(200);
    var swapping =
        new JasperReportRenderer(
            new ReportSettings("reports", 4, 10 * 1024 * 1024, Virtualization.swapFile(1, folder)));

    byte[] pdf = swapping.toBytes(orders(ReportFormat.PDF).withData(ReportData.beans(rows)));

    assertThat(Documents.pdfPages(pdf)).isGreaterThan(1);
    assertThat(Documents.pdfText(pdf)).contains("row1", "row200");
    assertThat(Documents.filesIn(folder)).isZero();
  }

  @Test
  void should_delete_the_swap_file_when_filling_fails() {
    var swapping =
        new JasperReportRenderer(
            new ReportSettings("reports", 4, 1024 * 1024, Virtualization.swapFile(1, folder)));
    var request =
        ReportRequest.of("parse.jrxml", ReportFormat.PDF)
            .withParameters(Map.of("number", "not a number"));

    assertThatThrownBy(() -> swapping.toBytes(request)).isInstanceOf(ReportException.class);
    assertThat(Documents.filesIn(folder)).isZero();
  }

  @Test
  void should_render_the_same_template_again_from_the_cache() {
    renderer.toBytes(orders(ReportFormat.PDF));

    assertThat(Documents.pdfText(renderer.toBytes(orders(ReportFormat.PDF)))).contains("Ana");
  }

  @Test
  void should_reject_a_missing_request() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> renderer.toBytes(null))
        .withMessage("request must not be null");
  }

  @Test
  void should_reject_a_missing_output() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> renderer.render(orders(ReportFormat.PDF), null))
        .withMessage("out must not be null");
  }

  @Test
  void should_render_an_empty_report_when_there_are_no_rows() {
    var request = orders(ReportFormat.PDF).withData(ReportData.beans(Collections.emptyList()));

    assertThat(Documents.pdfPages(renderer.toBytes(request))).isEqualTo(1);
  }

  private static ReportRequest orders(ReportFormat format) {
    return ReportRequest.of("orders.jrxml", format)
        .withParameters(Map.of("title", "Orders of the day"))
        .withData(ReportData.beans(ORDERS));
  }

  private static final class CloseTrackingStream extends OutputStream {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean closed;

    @Override
    public void write(int b) {
      buffer.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) {
      buffer.write(b, off, len);
    }

    @Override
    public void close() {
      closed = true;
    }

    byte[] toByteArray() {
      return buffer.toByteArray();
    }
  }
}
