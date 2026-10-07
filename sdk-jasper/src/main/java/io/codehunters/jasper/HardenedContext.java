package io.codehunters.jasper;

import java.util.List;
import net.sf.jasperreports.engine.DefaultJasperReportsContext;
import net.sf.jasperreports.engine.SimpleJasperReportsContext;
import net.sf.jasperreports.repo.RepositoryService;

/**
 * JasperReports' defaults with one change: resources come only from the classpath base folder.
 * Every other extension (fonts, components, exporters) is inherited. XML needs no setting here:
 * JasperReports 7 parses JRXML and JRTX with Jackson's XML mapper, which leaves DTDs unprocessed,
 * so a DOCTYPE's entities are never resolved.
 */
final class HardenedContext extends SimpleJasperReportsContext {

  private final List<RepositoryService> repositories;

  HardenedContext(ReportSettings settings, ClassLoader loader) {
    super(DefaultJasperReportsContext.getInstance());
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
