package io.codehunters.tracing.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.tracing.TraceIdFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

class WebTracingAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(WebTracingAutoConfiguration.class));

  @Test
  @DisplayName("should_register_TraceIdFilter_and_FilterRegistrationBean_when_autoconfig_runs")
  void should_register_filter_and_registration() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(TraceIdFilter.class);
          FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
          assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
          assertThat(registration.getFilter()).isInstanceOf(TraceIdFilter.class);
          assertThat(registration.getUrlPatterns()).contains("/*");
        });
  }
}
