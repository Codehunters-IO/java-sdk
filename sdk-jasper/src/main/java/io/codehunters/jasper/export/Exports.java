package io.codehunters.jasper.export;

import io.codehunters.jasper.ReportFormat;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRPropertiesUtil;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleWriterExporterOutput;
import net.sf.jasperreports.pdf.JRPdfExporter;
import net.sf.jasperreports.pdf.PdfExporterConfiguration;

/** Writes a filled report in one format, into a stream the caller keeps open. */
public final class Exports {

  // Levels a, the accessible PDF/A variants, need a tagged document like the tagged flag itself.
  private static final Set<String> TAGGED_PDFA = Set.of("pdfa1a", "pdfa2a", "pdfa3a");

  private Exports() {}

  /**
   * Whether the report asks for a tagged PDF, directly or through an accessible PDF/A level. The
   * OpenPDF release on Maven Central lacks the structure-tree API JasperReports tags with.
   */
  public static boolean asksForTaggedPdf(JasperPrint print, JasperReportsContext context) {
    var properties = JRPropertiesUtil.getInstance(context);
    String conformance =
        properties.getProperty(print, PdfExporterConfiguration.PROPERTY_PDFA_CONFORMANCE);
    return properties.getBooleanProperty(print, PdfExporterConfiguration.PROPERTY_TAGGED, false)
        || (conformance != null && TAGGED_PDFA.contains(conformance.toLowerCase(Locale.ROOT)));
  }

  public static void write(
      JasperPrint print, ReportFormat format, JasperReportsContext context, OutputStream out)
      throws JRException {
    switch (format) {
      case PDF -> pdf(print, context, out);
      case XLSX -> xlsx(print, context, out);
      case CSV -> csv(print, context, out);
    }
  }

  private static void pdf(JasperPrint print, JasperReportsContext context, OutputStream out)
      throws JRException {
    var exporter = new JRPdfExporter(context);
    exporter.setExporterInput(new SimpleExporterInput(print));
    exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
    exporter.exportReport();
  }

  private static void xlsx(JasperPrint print, JasperReportsContext context, OutputStream out)
      throws JRException {
    var exporter = new JRXlsxExporter(context);
    exporter.setExporterInput(new SimpleExporterInput(print));
    exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
    exporter.exportReport();
  }

  private static void csv(JasperPrint print, JasperReportsContext context, OutputStream out)
      throws JRException {
    var exporter = new JRCsvExporter(context);
    exporter.setExporterInput(new SimpleExporterInput(print));
    exporter.setExporterOutput(new SimpleWriterExporterOutput(out, StandardCharsets.UTF_8.name()));
    exporter.exportReport();
  }
}
