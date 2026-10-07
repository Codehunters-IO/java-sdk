package io.codehunters.jasper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.Map;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.jdt.JRJdtCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JasperHardeningTest {

  private final ClassLoader loader = getClass().getClassLoader();
  private final JasperReportRenderer renderer = new JasperReportRenderer();

  @Test
  void should_choose_the_jdt_compiler_which_reads_classes_through_the_class_loader()
      throws ReflectiveOperationException {
    // javac, JasperReports' fallback, sees only java.class.path: the outer jar of a Boot fat jar.
    var manager =
        JasperCompileManager.getInstance(new HardenedContext(ReportSettings.DEFAULT, loader));
    Method chosen = JasperCompileManager.class.getDeclaredMethod("getJavaCompiler");
    chosen.setAccessible(true);

    assertThat(chosen.invoke(manager)).isInstanceOf(JRJdtCompiler.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"tagged.jrxml", "pdfa1a.jrxml"})
  void should_report_tagged_pdf_as_unsupported_before_exporting(String template) {
    var request = ReportRequest.of(template, ReportFormat.PDF);

    assertThatThrownBy(() -> renderer.toBytes(request))
        .isInstanceOf(ReportException.class)
        .hasMessage(
            "report "
                + template
                + " asks for tagged PDF or PDF/A level A, which the OpenPDF release on Maven"
                + " Central cannot write")
        .hasNoCause();
  }

  @Test
  void should_refuse_a_template_with_a_doctype_in_the_xml_parser() {
    var request = ReportRequest.of("xxe.jrxml", ReportFormat.PDF);

    assertThatThrownBy(() -> renderer.toBytes(request))
        .isInstanceOf(ReportException.class)
        .hasMessage("template xxe.jrxml could not be compiled")
        .hasStackTraceContaining("Undeclared general entity \"secret\"");
  }

  @Test
  void should_apply_a_style_template_read_from_the_base_folder() {
    var request =
        ReportRequest.of("styled.jrxml", ReportFormat.PDF)
            .withParameters(Map.of("styles", "styles.jrtx"));

    assertThat(Documents.pdfText(renderer.toBytes(request))).contains("Styled heading");
  }

  @Test
  void should_refuse_a_style_template_with_a_doctype() {
    var request =
        ReportRequest.of("styled.jrxml", ReportFormat.PDF)
            .withParameters(Map.of("styles", "xxe.jrtx"));

    assertThatThrownBy(() -> renderer.toBytes(request))
        .isInstanceOf(ReportException.class)
        .hasMessage("report styled.jrxml could not be filled")
        .hasStackTraceContaining("Undeclared general entity \"secret\"");
  }
}
