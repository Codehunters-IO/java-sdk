package io.codehunters.jasper;

import java.io.OutputStream;
import java.util.HashMap;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperReportsContext;

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
    try (var swap = Swap.open(settings.virtualization(), context)) {
      export(request, fill(request, report, swap), out);
    }
  }

  @Override
  public byte[] toBytes(ReportRequest request) {
    var out = new CappedOutputStream(settings.maxOutputBytes());
    render(request, out);
    return out.toByteArray();
  }

  private JasperPrint fill(ReportRequest request, JasperReport report, Swap swap) {
    var parameters = new HashMap<>(request.parameters());
    swap.attachTo(parameters);
    try {
      var print =
          JasperFillManager.getInstance(context).fill(report, parameters, request.data().open());
      swap.freeze();
      return print;
    } catch (JRException | RuntimeException e) {
      throw failure("report " + request.template() + " could not be filled", e);
    }
  }

  private void export(ReportRequest request, JasperPrint print, OutputStream out) {
    try {
      Exports.write(print, request.format(), context, out);
    } catch (JRException | RuntimeException e) {
      throw failure(
          "report " + request.template() + " could not be exported to " + request.format(), e);
    }
  }

  // The output limit is reported as itself even when an exporter wrapped it.
  private static ReportException failure(String message, Exception e) {
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      if (cause instanceof ReportException known) {
        return known;
      }
    }
    return new ReportException(message, e);
  }
}
