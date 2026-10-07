package io.codehunters.jasper;

import java.io.InputStream;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.repo.DefaultRepositoryService;
import net.sf.jasperreports.repo.RepositoryContext;
import net.sf.jasperreports.repo.RepositoryResourceContext;
import net.sf.jasperreports.repo.ResourceInfo;

/**
 * The only place a report reads images, subreports and styles named by a String from: the template
 * base folder on the classpath. A name is looked up beside the template (or subreport) that uses it
 * first, then from the base folder. File paths, URLs and anything that leaves the folder resolve to
 * nothing, so a String location that reaches a report through a parameter cannot read the disk or
 * the network. Values of other types never get here; {@link ReportRequest} refuses the ones
 * JasperReports would load directly.
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
    InputStream beside = besideUser(context, uri);
    return beside != null ? beside : loader.getResourceAsStream(settings.resolve(uri));
  }

  // The context location is the name of the template or subreport the resource is used from.
  private InputStream besideUser(RepositoryContext context, String uri) {
    RepositoryResourceContext user = context == null ? null : context.getResourceContext();
    String location = user == null ? null : user.getContextLocation();
    int slash = location == null ? -1 : location.lastIndexOf('/');
    if (slash < 1 || !TemplateNames.isResource(location)) {
      return null;
    }
    return loader.getResourceAsStream(settings.resolve(location.substring(0, slash + 1) + uri));
  }

  @Override
  public ResourceInfo getResourceInfo(RepositoryContext context, String location) {
    return null;
  }
}
