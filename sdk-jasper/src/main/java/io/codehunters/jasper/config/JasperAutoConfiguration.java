package io.codehunters.jasper.config;

import io.codehunters.jasper.JasperReportRenderer;
import io.codehunters.jasper.ReportRenderer;
import io.codehunters.jasper.metrics.MeteredReportRenderer;
import io.micrometer.core.instrument.MeterRegistry;
import net.sf.jasperreports.engine.JasperReport;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;

/**
 * Registers a {@link ReportRenderer} reading templates from the application's classpath, timed when
 * Micrometer and a single {@link MeterRegistry} are present. Backs off when the application defines
 * its own renderer; {@code codehunters.jasper.enabled=false} disables the SDK.
 */
@AutoConfiguration
@ConditionalOnClass(JasperReport.class)
@ConditionalOnBooleanProperty(name = "codehunters.jasper.enabled", matchIfMissing = true)
@EnableConfigurationProperties(JasperProperties.class)
public class JasperAutoConfiguration {

  private static final String METER_REGISTRY = "io.micrometer.core.instrument.MeterRegistry";

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public JasperAutoConfiguration() {}

  /**
   * The renderer, wrapped in a {@link MeteredReportRenderer} when Micrometer is on the classpath
   * and a single {@link MeterRegistry} exists.
   *
   * @param properties the SDK configuration
   * @param resourceLoader supplies the application's class loader
   * @param beanFactory looked up for a meter registry, only when Micrometer is present
   * @return the renderer
   */
  @Bean
  @ConditionalOnMissingBean(ReportRenderer.class)
  public ReportRenderer reportRenderer(
      JasperProperties properties, ResourceLoader resourceLoader, BeanFactory beanFactory) {
    ReportRenderer renderer = renderer(properties, resourceLoader);
    return ClassUtils.isPresent(METER_REGISTRY, resourceLoader.getClassLoader())
        ? Metering.wrap(renderer, beanFactory)
        : renderer;
  }

  private static JasperReportRenderer renderer(
      JasperProperties properties, ResourceLoader resourceLoader) {
    ClassLoader loader = resourceLoader.getClassLoader();
    return loader == null
        ? new JasperReportRenderer(properties.settings())
        : new JasperReportRenderer(properties.settings(), loader);
  }

  // Holds every Micrometer reference, so the class is loaded only when Micrometer is present.
  private static final class Metering {

    private Metering() {}

    static ReportRenderer wrap(ReportRenderer renderer, BeanFactory beanFactory) {
      MeterRegistry meters = beanFactory.getBeanProvider(MeterRegistry.class).getIfUnique();
      return meters == null ? renderer : new MeteredReportRenderer(renderer, meters);
    }
  }
}
