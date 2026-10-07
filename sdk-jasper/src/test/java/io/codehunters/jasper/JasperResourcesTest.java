package io.codehunters.jasper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JasperResourcesTest {

  private final JasperReportRenderer renderer = new JasperReportRenderer();

  @TempDir Path folder;

  @Test
  void should_load_an_image_from_the_folder_of_a_template_in_a_subfolder() {
    var request =
        ReportRequest.of("sub/image.jrxml", ReportFormat.PDF)
            .withParameters(Map.of("image", "badge.png"));

    assertThat(Documents.pdfPages(renderer.toBytes(request))).isEqualTo(1);
  }

  @Test
  void should_fall_back_to_the_base_folder_for_a_resource_not_beside_the_template() {
    var request =
        ReportRequest.of("sub/image.jrxml", ReportFormat.PDF)
            .withParameters(Map.of("image", "logo.png"));

    assertThat(Documents.pdfPages(renderer.toBytes(request))).isEqualTo(1);
  }

  @Test
  void should_fill_a_subreport_named_beside_its_master() throws Exception {
    Path sales = Files.createDirectories(folder.resolve("reports/sales"));
    copy("master.jrxml", sales.resolve("master.jrxml"));
    compile("note.jrxml", sales.resolve("note.jasper"));
    var loader = new URLClassLoader(new URL[] {folder.toUri().toURL()}, null);
    var request =
        ReportRequest.of("sales/master.jrxml", ReportFormat.PDF)
            .withParameters(Map.of("part", "note.jasper"));

    byte[] pdf = new JasperReportRenderer(ReportSettings.DEFAULT, loader).toBytes(request);

    assertThat(Documents.pdfText(pdf)).contains("Master", "From the subreport");
  }

  @Test
  void should_render_one_template_from_two_threads_at_once() {
    var request =
        ReportRequest.of("orders.jrxml", ReportFormat.PDF)
            .withParameters(Map.of("title", "Concurrent"))
            .withData(ReportData.beans(Order.numbered(50)));

    var first = CompletableFuture.supplyAsync(() -> renderer.toBytes(request));
    var second = CompletableFuture.supplyAsync(() -> renderer.toBytes(request));

    assertThat(Documents.pdfText(first.join())).contains("Concurrent", "row50");
    assertThat(Documents.pdfText(second.join())).contains("Concurrent", "row50");
  }

  private void copy(String resource, Path target) throws IOException {
    try (var in = getClass().getResourceAsStream("/reports/" + resource)) {
      Files.copy(in, target);
    }
  }

  private void compile(String resource, Path target) throws IOException, JRException {
    try (var in = getClass().getResourceAsStream("/reports/" + resource);
        var out = Files.newOutputStream(target)) {
      JasperCompileManager.compileReportToStream(in, out);
    }
  }
}
