package io.codehunters.jasper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.fill.JRFiller;
import net.sf.jasperreports.engine.fill.ReportFiller;
import net.sf.jasperreports.engine.fill.SimpleJasperReportSource;
import net.sf.jasperreports.governors.MaxPagesGovernorException;
import net.sf.jasperreports.governors.TimeoutGovernorException;
import net.sf.jasperreports.repo.SimpleRepositoryResourceContext;

/**
 * Renders JasperReports 7 templates read from a classpath folder. Templates are compiled once and
 * cached; resources (images, subreports) load only from the same folder; pages may be swapped to a
 * file for large reports. Thread-safe.
 *
 * <p>Templates are code: their expressions run as Java with the application's permissions. Load
 * only templates shipped with the application, never one a user supplied.
 */
public final class JasperReportRenderer implements ReportRenderer {

  private final ReportSettings settings;
  private final JasperReportsContext context;
  private final TemplateCache<JasperReport> templates;
  private final SwapFolder swaps;

  /** Creates a renderer with {@link ReportSettings#DEFAULT}. */
  public JasperReportRenderer() {
    this(ReportSettings.DEFAULT);
  }

  /**
   * Creates a renderer that reads templates through the class loader that loaded this class.
   *
   * @param settings where templates are and how work is bounded
   */
  public JasperReportRenderer(ReportSettings settings) {
    this(settings, JasperReportRenderer.class.getClassLoader());
  }

  /**
   * Creates a renderer that reads templates through the given class loader.
   *
   * @param settings where templates are and how work is bounded
   * @param loader the class loader templates and their resources are read from
   */
  public JasperReportRenderer(ReportSettings settings, ClassLoader loader) {
    if (settings == null || loader == null) {
      throw new IllegalArgumentException("settings and loader must not be null");
    }
    this.settings = settings;
    this.context = new HardenedContext(settings, loader);
    var templateLoader = new TemplateLoader(settings, context, loader);
    this.templates = new TemplateCache<>(settings.cacheSize(), templateLoader::load);
    this.swaps = new SwapFolder(settings.virtualization().directory());
  }

  @Override
  public void render(ReportRequest request, OutputStream out) {
    if (request == null) {
      throw new IllegalArgumentException("request must not be null");
    }
    if (out == null) {
      throw new IllegalArgumentException("out must not be null");
    }
    var report = templates.get(request.template());
    try (var swap = openSwap(request)) {
      export(request, fill(request, report, swap), out);
    }
  }

  /** Deletes the renderer's swap folder; renders after this create a new one. */
  @Override
  public void close() {
    swaps.close();
  }

  @Override
  public byte[] toBytes(ReportRequest request) {
    var out = new CappedOutputStream(settings.maxOutputBytes());
    render(request, out);
    return out.toByteArray();
  }

  private Swap openSwap(ReportRequest request) {
    try {
      return Swap.open(settings.virtualization(), context, swaps);
    } catch (IOException | RuntimeException e) {
      throw new ReportException(
          "the swap folder for report " + request.template() + " could not be created", e);
    }
  }

  private JasperPrint fill(ReportRequest request, JasperReport report, Swap swap) {
    var parameters = new HashMap<>(request.parameters());
    swap.attachTo(parameters);
    JRDataSource source = request.data().open();
    try (var closing = closing(source)) {
      var print = filler(request, report).fill(parameters, source);
      swap.freeze();
      return print;
    } catch (Exception e) {
      // JasperReports, the source's close and the governors all fail as plain exceptions here.
      throw fillFailure(request, e);
    }
  }

  // Naming the template gives its resources a folder to be looked up beside.
  private ReportFiller filler(ReportRequest request, JasperReport report) throws JRException {
    String template = request.template();
    var source =
        SimpleJasperReportSource.from(
            report, template, SimpleRepositoryResourceContext.of(template));
    return JRFiller.createReportFiller(context, source);
  }

  private ReportException fillFailure(ReportRequest request, Exception e) {
    var limits = settings.fillLimits();
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      if (cause instanceof MaxPagesGovernorException) {
        return new ReportException(
            "report " + request.template() + " exceeds the page limit (" + limits.maxPages() + ")",
            e);
      }
      if (cause instanceof TimeoutGovernorException) {
        return new ReportException(
            "report " + request.template() + " exceeds the fill timeout (" + limits.timeout() + ")",
            e);
      }
    }
    return failure("report " + request.template() + " could not be filled", e);
  }

  // A source backed by a cursor or a stream is released after the fill, successful or not.
  private static AutoCloseable closing(JRDataSource source) {
    return source instanceof AutoCloseable closeable ? closeable : () -> {};
  }

  private void export(ReportRequest request, JasperPrint print, OutputStream out) {
    if (request.format() == ReportFormat.PDF && Exports.asksForTaggedPdf(print, context)) {
      throw new ReportException(
          "report "
              + request.template()
              + " asks for tagged PDF or PDF/A level A, which the OpenPDF release on Maven Central"
              + " cannot write");
    }
    try {
      Exports.write(print, request.format(), context, out);
    } catch (JRException | RuntimeException | LinkageError e) {
      // A LinkageError is OpenPDF missing an API a JasperReports exporter calls, not a broken JVM.
      throw failure(
          "report " + request.template() + " could not be exported to " + request.format(), e);
    }
  }

  // The output limit is reported as itself even when an exporter wrapped it.
  private static ReportException failure(String message, Throwable e) {
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      if (cause instanceof ReportException known) {
        return known;
      }
    }
    return new ReportException(message, e);
  }
}
