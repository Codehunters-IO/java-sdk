package io.codehunters.jasper.metrics;

import io.codehunters.jasper.JasperReportRenderer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;

/**
 * Publishes the compiled-template cache of a {@link JasperReportRenderer} through Micrometer's
 * standard cache meters ({@code cache.gets} with {@code result=hit|miss}, {@code cache.puts},
 * {@code cache.evictions}, {@code cache.size}, ...), tagged {@code cache=}{@value #CACHE_NAME}.
 * Registers nothing when the renderer's cache is off ({@code cache-size} 0). The auto-configuration
 * binds it when a single {@link MeterRegistry} exists; without Spring, call {@link
 * #bindTo(MeterRegistry)}.
 */
public final class TemplateCacheMetrics implements MeterBinder {

  /** The value of the {@code cache} tag on every meter. */
  public static final String CACHE_NAME = "codehunters.jasper.template.cache";

  private final JasperReportRenderer renderer;

  /**
   * Creates the binder.
   *
   * @param renderer the renderer whose template cache is published
   */
  public TemplateCacheMetrics(JasperReportRenderer renderer) {
    if (renderer == null) {
      throw new IllegalArgumentException("renderer must not be null");
    }
    this.renderer = renderer;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    var templates = renderer.templateCache();
    if (templates.isEnabled()) {
      CaffeineCacheMetrics.monitor(registry, templates.entries(), CACHE_NAME, Tags.empty());
    }
  }
}
