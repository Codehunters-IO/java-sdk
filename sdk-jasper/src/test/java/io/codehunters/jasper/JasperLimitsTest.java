package io.codehunters.jasper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;
import net.sf.jasperreports.engine.util.JRSwapFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JasperLimitsTest {

  private static final Duration GENEROUS = Duration.ofMinutes(1);

  @TempDir Path folder;

  @Test
  void should_stop_a_report_that_grows_past_the_page_limit() {
    var renderer = new JasperReportRenderer(settings(new FillLimits(1, GENEROUS)));
    var request = orders().withData(ReportData.beans(Order.numbered(200)));

    assertThatThrownBy(() -> renderer.render(request, new java.io.ByteArrayOutputStream()))
        .isInstanceOf(ReportException.class)
        .hasMessage("report orders.jrxml exceeds the page limit (1)");
  }

  @Test
  void should_stop_a_report_that_fills_for_longer_than_the_timeout() {
    var renderer = new JasperReportRenderer(settings(new FillLimits(100, Duration.ofMillis(50))));
    var request = orders().withData(ReportData.source(() -> new SlowSource(Duration.ofMillis(5))));

    assertThatThrownBy(() -> renderer.toBytes(request))
        .isInstanceOf(ReportException.class)
        .hasMessage("report orders.jrxml exceeds the fill timeout (PT0.05S)");
  }

  @Test
  void should_close_a_supplied_data_source_after_filling() {
    var source = new ClosingSource();
    var request = orders().withData(ReportData.source(() -> source));

    new JasperReportRenderer().toBytes(request);

    assertThat(source.isClosed()).isTrue();
  }

  @Test
  void should_never_register_swap_files_for_deletion_at_exit() {
    var context = new HardenedContext(ReportSettings.DEFAULT, getClass().getClassLoader());

    assertThat(context.getProperty(JRSwapFile.PROPERTY_DELETE_ON_EXIT)).isEqualTo("false");
  }

  @Test
  void should_swap_into_a_private_folder_only_the_owner_can_open() {
    var renderer = swapping(folder);

    renderer.toBytes(orders().withData(ReportData.beans(Order.numbered(200))));

    assertThat(Documents.regularFilesUnder(folder)).isZero();
    assertThat(Documents.filesIn(folder)).isOne();
    assertThat(Documents.permissionsOfOnlyFolderIn(folder))
        .isEqualTo(PosixFilePermissions.fromString("rwx------"));
  }

  @Test
  void should_remove_its_private_swap_folder_when_closed() {
    var renderer = swapping(folder);
    renderer.toBytes(orders().withData(ReportData.beans(Order.numbered(200))));

    renderer.close();

    assertThat(Documents.filesIn(folder)).isZero();
  }

  @Test
  void should_report_a_swap_folder_that_cannot_be_created() {
    var renderer = swapping(folder.resolve("missing"));

    assertThatThrownBy(() -> renderer.toBytes(orders()))
        .isInstanceOf(ReportException.class)
        .hasMessage("the swap folder for report orders.jrxml could not be created");
  }

  private static ReportSettings settings(FillLimits limits) {
    return new ReportSettings("reports", 4, 10 * 1024 * 1024, limits, Virtualization.OFF);
  }

  private static JasperReportRenderer swapping(Path directory) {
    return new JasperReportRenderer(
        new ReportSettings(
            "reports",
            4,
            10 * 1024 * 1024,
            FillLimits.DEFAULT,
            Virtualization.swapFile(1, directory)));
  }

  private static ReportRequest orders() {
    return ReportRequest.of("orders.jrxml", ReportFormat.PDF)
        .withParameters(Map.of("title", "Orders"));
  }
}
