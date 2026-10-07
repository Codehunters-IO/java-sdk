package io.codehunters.jasper;

import java.util.List;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.SimpleJasperReportsContext;
import net.sf.jasperreports.engine.util.JRSwapFile;
import net.sf.jasperreports.governors.MaxPagesGovernor;
import net.sf.jasperreports.governors.TimeoutGovernor;
import net.sf.jasperreports.repo.RepositoryService;

/**
 * JasperReports' defaults with these changes: resources come only from the classpath base folder,
 * the page and time governors carry the configured fill limits, and swap files are not registered
 * for deletion at exit (the renderer deletes them, and the JDK's exit list would only grow). Every
 * other extension (fonts, components, exporters) is inherited. XML needs no setting here:
 * JasperReports 7 parses JRXML and JRTX with Jackson's XML mapper, which leaves DTDs unprocessed,
 * so a DOCTYPE's entities are never resolved.
 */
final class HardenedContext extends SimpleJasperReportsContext {

  private final List<RepositoryService> repositories;

  HardenedContext(ReportSettings settings, ClassLoader loader) {
    super(DefaultJasperReportsContext.getInstance());
    var limits = settings.fillLimits();
    setProperty(MaxPagesGovernor.PROPERTY_MAX_PAGES_ENABLED, "true");
    setProperty(MaxPagesGovernor.PROPERTY_MAX_PAGES, String.valueOf(limits.maxPages()));
    setProperty(TimeoutGovernor.PROPERTY_TIMEOUT_ENABLED, "true");
    setProperty(TimeoutGovernor.PROPERTY_TIMEOUT, String.valueOf(limits.timeout().toMillis()));
    setProperty(JRSwapFile.PROPERTY_DELETE_ON_EXIT, "false");
    this.repositories = List.of(new ClasspathRepositoryService(this, settings, loader));
  }

  // The parent's default repository would add file and URL loading back; it is replaced, not
  // merged as SimpleJasperReportsContext does with its own extensions.
  @Override
  @SuppressWarnings("unchecked")
  public <T> List<T> getExtensions(Class<T> extensionType) {
    if (extensionType == RepositoryService.class) {
      return (List<T>) repositories;
    }
    return super.getExtensions(extensionType);
  }
}
