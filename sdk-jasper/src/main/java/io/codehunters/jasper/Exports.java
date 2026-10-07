package io.codehunters.jasper;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimpleWriterExporterOutput;
import net.sf.jasperreports.pdf.JRPdfExporter;

/** Writes a filled report in one format, into a stream the caller keeps open. */
final class Exports {

  private Exports() {}

  static void write(
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
