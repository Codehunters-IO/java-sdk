package io.codehunters.jasper.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.codehunters.jasper.JasperReportRenderer;
import io.codehunters.jasper.ReportFormat;
import io.codehunters.jasper.ReportRequest;
import io.codehunters.jasper.ReportSettings;
import io.codehunters.jasper.Virtualization;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class TemplateCacheMetricsTest {

  private static final ReportRequest THIS = ReportRequest.of("this.jrxml", ReportFormat.CSV);

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @Test
  void should_count_template_cache_hits_and_misses() {
    var renderer = new JasperReportRenderer();
    new TemplateCacheMetrics(renderer).bindTo(registry);

    renderer.toBytes(THIS);
    renderer.toBytes(THIS);

    assertThat(gets("hit")).isEqualTo(1.0);
    assertThat(gets("miss")).isEqualTo(1.0);
  }

  @Test
  void should_name_the_cache_codehunters_jasper_template_cache() {
    assertThat(TemplateCacheMetrics.CACHE_NAME).isEqualTo("codehunters.jasper.template.cache");
  }

  @Test
  void should_register_nothing_when_the_cache_is_off() {
    var uncached =
        new JasperReportRenderer(new ReportSettings("reports", 0, 1024, Virtualization.OFF));

    new TemplateCacheMetrics(uncached).bindTo(registry);

    assertThat(registry.getMeters()).isEmpty();
  }

  @Test
  void should_reject_a_missing_renderer() {
    assertThatIllegalArgumentException().isThrownBy(() -> new TemplateCacheMetrics(null));
  }

  private double gets(String result) {
    return registry
        .get("cache.gets")
        .tag("cache", TemplateCacheMetrics.CACHE_NAME)
        .tag("result", result)
        .functionCounter()
        .count();
  }
}
