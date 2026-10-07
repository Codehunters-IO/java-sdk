package io.codehunters.jasper;

import java.util.List;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.SimpleJasperReportsContext;
import net.sf.jasperreports.repo.DefaultRepositoryService;
import net.sf.jasperreports.repo.RepositoryService;

/**
 * JasperReports' defaults with three changes: resources come only from the classpath base folder,
 * JRXML may not declare a DOCTYPE, and unknown XML entities are never loaded. Every other extension
 * (fonts, components, exporters) is inherited.
 */
final class HardenedContext extends SimpleJasperReportsContext {

  private final List<RepositoryService> repositories;

  HardenedContext(ReportSettings settings, ClassLoader loader) {
    super(DefaultJasperReportsContext.getInstance());
    setProperty("net.sf.jasperreports.xml.allow.doctype", "false");
    setProperty("net.sf.jasperreports.xml.load.unknown.entities", "false");
    setProperty(DefaultRepositoryService.PROPERTY_FILES_ENABLED, "false");
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
