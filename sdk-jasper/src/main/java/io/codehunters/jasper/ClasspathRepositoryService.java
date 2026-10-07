package io.codehunters.jasper;

import java.io.InputStream;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.repo.DefaultRepositoryService;
import net.sf.jasperreports.repo.RepositoryContext;
import net.sf.jasperreports.repo.ResourceInfo;

/**
 * The only place a report reads images, subreports and styles from: the template base folder on the
 * classpath. File paths, URLs and anything that leaves the folder resolve to nothing, so a location
 * that reaches a report through a parameter cannot read the disk or the network.
 */
final class ClasspathRepositoryService extends DefaultRepositoryService {

  private final ReportSettings settings;
  private final ClassLoader loader;

  ClasspathRepositoryService(
      JasperReportsContext context, ReportSettings settings, ClassLoader loader) {
    super(context);
    this.settings = settings;
    this.loader = loader;
  }

  @Override
  public InputStream getInputStream(RepositoryContext context, String uri) {
    if (!TemplateNames.isResource(uri)) {
      return null;
    }
    return loader.getResourceAsStream(settings.resolve(uri));
  }

  @Override
  public ResourceInfo getResourceInfo(RepositoryContext context, String location) {
    return null;
  }
}
