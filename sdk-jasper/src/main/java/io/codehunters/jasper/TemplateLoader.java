package io.codehunters.jasper;

import java.io.IOException;
import java.io.InputStream;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.util.JRLoader;

/** Reads a template from the base folder: a {@code .jasper} as is, a {@code .jrxml} compiled. */
final class TemplateLoader {

  private final ReportSettings settings;
  private final JasperReportsContext context;
  private final ClassLoader loader;

  TemplateLoader(ReportSettings settings, JasperReportsContext context, ClassLoader loader) {
    this.settings = settings;
    this.context = context;
    this.loader = loader;
  }

  JasperReport load(String name) {
    try (InputStream in = loader.getResourceAsStream(settings.resolve(name))) {
      if (in == null) {
        throw new ReportException(
            "template " + name + " was not found under " + settings.templateBase());
      }
      return name.endsWith(".jasper") ? precompiled(name, in) : compile(name, in);
    } catch (IOException e) {
      throw new ReportException("template " + name + " could not be read", e);
    }
  }

  private JasperReport compile(String name, InputStream in) {
    try {
      return JasperCompileManager.getInstance(context).compile(in);
    } catch (JRException | RuntimeException e) {
      throw new ReportException("template " + name + " could not be compiled", e);
    }
  }

  private JasperReport precompiled(String name, InputStream in) {
    try {
      if (JRLoader.loadObject(context, in) instanceof JasperReport report) {
        return report;
      }
      throw new ReportException("template " + name + " is not a compiled report");
    } catch (JRException | RuntimeException e) {
      throw failure("template " + name + " could not be loaded", e);
    }
  }

  private static ReportException failure(String message, Exception e) {
    return e instanceof ReportException known ? known : new ReportException(message, e);
  }
}
