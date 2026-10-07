package io.codehunters.jasper.config;

import io.codehunters.jasper.JasperReportRenderer;
import io.codehunters.jasper.ReportRenderer;
import io.codehunters.jasper.metrics.MeteredReportRenderer;
import io.micrometer.core.instrument.MeterRegistry;
import net.sf.jasperreports.engine.JasperReport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

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

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public JasperAutoConfiguration() {}

  /**
   * The renderer, used when Micrometer is not on the classpath.
   *
   * @param properties the SDK configuration
   * @param resourceLoader supplies the application's class loader
   * @return the renderer
   */
  @Bean
  @ConditionalOnMissingBean(ReportRenderer.class)
  public ReportRenderer reportRenderer(JasperProperties properties, ResourceLoader resourceLoader) {
    return renderer(properties, resourceLoader);
  }

  static JasperReportRenderer renderer(JasperProperties properties, ResourceLoader resourceLoader) {
    ClassLoader loader = resourceLoader.getClassLoader();
    return loader == null
        ? new JasperReportRenderer(properties.settings())
        : new JasperReportRenderer(properties.settings(), loader);
  }

  /** Times renders when Micrometer is on the classpath and a single registry exists. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
  static class MeteredRendererConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReportRenderer.class)
    ReportRenderer meteredReportRenderer(
        JasperProperties properties,
        ResourceLoader resourceLoader,
        ObjectProvider<MeterRegistry> registry) {
      ReportRenderer renderer = renderer(properties, resourceLoader);
      MeterRegistry meters = registry.getIfUnique();
      return meters == null ? renderer : new MeteredReportRenderer(renderer, meters);
    }
  }
}
