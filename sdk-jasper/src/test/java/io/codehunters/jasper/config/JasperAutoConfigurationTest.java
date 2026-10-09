package io.codehunters.jasper.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.jasper.JasperReportRenderer;
import io.codehunters.jasper.ReportData;
import io.codehunters.jasper.ReportException;
import io.codehunters.jasper.ReportFormat;
import io.codehunters.jasper.ReportRenderer;
import io.codehunters.jasper.ReportRequest;
import io.codehunters.jasper.metrics.MeteredReportRenderer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.JasperReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class JasperAutoConfigurationTest {

  private static final ReportRequest ORDERS =
      ReportRequest.of("orders.jrxml", ReportFormat.PDF)
          .withParameters(Map.of("title", "Orders"))
          .withData(ReportData.beans(List.of(new Row("Ana", 10))));

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(JasperAutoConfiguration.class));

  @TempDir Path folder;

  public record Row(String name, Integer amount) {}

  private static List<Row> rows(int count) {
    return java.util.stream.IntStream.rangeClosed(1, count)
        .mapToObj(i -> new Row("row" + i, i))
        .toList();
  }

  @Configuration(proxyBeanMethods = false)
  static class OwnRenderer {
    static final ReportRenderer RENDERER =
        new ReportRenderer() {
          @Override
          public void render(ReportRequest request, OutputStream out) {}

          @Override
          public byte[] toBytes(ReportRequest request) {
            return new byte[0];
          }
        };

    @Bean
    ReportRenderer ownRenderer() {
      return RENDERER;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class Meters {
    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  @Test
  void should_register_a_renderer_for_the_reports_folder() {
    runner.run(
        context ->
            assertThat(context.getBean(ReportRenderer.class).toBytes(ORDERS))
                .startsWith('%', 'P', 'D', 'F'));
  }

  @Test
  void should_register_a_plain_renderer_when_no_meter_registry_exists() {
    runner.run(
        context ->
            assertThat(context.getBean(ReportRenderer.class))
                .isInstanceOf(JasperReportRenderer.class));
  }

  @Test
  void should_time_renders_when_a_meter_registry_exists() {
    runner
        .withUserConfiguration(Meters.class)
        .run(
            context -> {
              context.getBean(ReportRenderer.class).toBytes(ORDERS);

              assertThat(context.getBean(ReportRenderer.class))
                  .isInstanceOf(MeteredReportRenderer.class);
              assertThat(
                      context
                          .getBean(MeterRegistry.class)
                          .get("report.render")
                          .tag("template", "orders.jrxml")
                          .tag("format", "pdf")
                          .tag("outcome", "SUCCESS")
                          .timer()
                          .count())
                  .isEqualTo(1);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class TwoMeters {
    @Bean
    MeterRegistry firstRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    MeterRegistry secondRegistry() {
      return new SimpleMeterRegistry();
    }
  }

  @Test
  void should_declare_a_single_renderer_bean_when_a_meter_registry_exists() {
    runner
        .withUserConfiguration(Meters.class)
        .run(
            context ->
                assertThat(context.getBeanNamesForType(ReportRenderer.class))
                    .containsExactly("reportRenderer"));
  }

  @Test
  void should_register_a_plain_renderer_when_the_meter_registry_is_ambiguous() {
    runner
        .withUserConfiguration(TwoMeters.class)
        .run(
            context ->
                assertThat(context.getBean(ReportRenderer.class))
                    .isInstanceOf(JasperReportRenderer.class));
  }

  @Test
  void should_back_off_when_the_application_defines_its_own_renderer_without_meters() {
    runner
        .withUserConfiguration(OwnRenderer.class)
        .run(
            context ->
                assertThat(context).getBean(ReportRenderer.class).isSameAs(OwnRenderer.RENDERER));
  }

  @Test
  void should_register_a_plain_renderer_without_micrometer_on_the_classpath() {
    runner
        .withClassLoader(new FilteredClassLoader(MeterRegistry.class))
        .run(
            context ->
                assertThat(context.getBean(ReportRenderer.class))
                    .isInstanceOf(JasperReportRenderer.class));
  }

  @Test
  void should_apply_the_configured_output_limit() {
    runner
        .withPropertyValues("codehunters.jasper.max-output-size=100B")
        .run(
            context ->
                assertThatThrownBy(() -> context.getBean(ReportRenderer.class).toBytes(ORDERS))
                    .isInstanceOf(ReportException.class)
                    .hasMessage("the report exceeds the output limit of 100 bytes"));
  }

  @Test
  void should_apply_the_configured_template_base() {
    runner
        .withPropertyValues("codehunters.jasper.template-base=elsewhere")
        .run(
            context ->
                assertThatThrownBy(() -> context.getBean(ReportRenderer.class).toBytes(ORDERS))
                    .isInstanceOf(ReportException.class)
                    .hasMessage("template orders.jrxml was not found under elsewhere"));
  }

  @Test
  void should_swap_pages_to_the_configured_folder_and_clean_it() throws Exception {
    runner
        .withPropertyValues(
            "codehunters.jasper.virtualizer.enabled=true",
            "codehunters.jasper.virtualizer.max-pages=1",
            "codehunters.jasper.virtualizer.directory=" + folder)
        .run(
            context -> {
              context.getBean(ReportRenderer.class).toBytes(ORDERS);

              try (var files = Files.walk(folder)) {
                assertThat(files.filter(Files::isRegularFile)).isEmpty();
              }
            });
  }

  @Test
  void should_remove_the_swap_folder_when_the_context_closes() throws Exception {
    runner
        .withPropertyValues(
            "codehunters.jasper.virtualizer.enabled=true",
            "codehunters.jasper.virtualizer.max-pages=1",
            "codehunters.jasper.virtualizer.directory=" + folder)
        .run(context -> context.getBean(ReportRenderer.class).toBytes(ORDERS));

    try (var files = Files.list(folder)) {
      assertThat(files).isEmpty();
    }
  }

  @Test
  void should_apply_the_configured_page_limit() {
    runner
        .withPropertyValues("codehunters.jasper.max-pages=1")
        .run(
            context ->
                assertThatThrownBy(
                        () ->
                            context
                                .getBean(ReportRenderer.class)
                                .toBytes(ORDERS.withData(ReportData.beans(rows(200)))))
                    .isInstanceOf(ReportException.class)
                    .hasMessage("report orders.jrxml exceeds the page limit (1)"));
  }

  @Test
  void should_reject_an_invalid_timeout_at_startup() {
    runner
        .withPropertyValues("codehunters.jasper.timeout=0s")
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .hasMessage("codehunters.jasper.timeout must be between 1 ms and 1 hour"));
  }

  @Test
  void should_back_off_when_the_application_defines_its_own_renderer() {
    runner
        .withUserConfiguration(OwnRenderer.class, Meters.class)
        .run(
            context ->
                assertThat(context).getBean(ReportRenderer.class).isSameAs(OwnRenderer.RENDERER));
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner
        .withPropertyValues("codehunters.jasper.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(ReportRenderer.class));
  }

  @Test
  void should_register_nothing_without_jasperreports_on_the_classpath() {
    runner
        .withClassLoader(new FilteredClassLoader(JasperReport.class))
        .run(context -> assertThat(context).doesNotHaveBean(ReportRenderer.class));
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "template-base=/reports | codehunters.jasper.template-base must be a relative classpath"
            + " folder made of letters, digits, '.', '_', '-' and '/'",
        "cache-size=-1 | codehunters.jasper.cache-size must be between 0 and 10000",
        "max-output-size=0B | codehunters.jasper.max-output-size must be between 1 and"
            + " 2147483639 bytes",
        "virtualizer.max-pages=0 | codehunters.jasper.virtualizer.max-pages must be between 1"
            + " and 100000"
      })
  void should_fail_startup_on_settings_outside_their_range(String property, String message) {
    runner
        .withPropertyValues("codehunters.jasper." + property)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(message));
  }

  @Test
  void should_fail_startup_when_the_swap_folder_does_not_exist() {
    runner
        .withPropertyValues(
            "codehunters.jasper.virtualizer.enabled=true",
            "codehunters.jasper.virtualizer.directory=" + folder.resolve("missing"))
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(
                        "codehunters.jasper.virtualizer.directory must be an existing, writable"
                            + " folder"));
  }
}
