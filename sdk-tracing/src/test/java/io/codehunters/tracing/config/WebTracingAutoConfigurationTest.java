package io.codehunters.tracing.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.tracing.servlet.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

class WebTracingAutoConfigurationTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(WebTracingAutoConfiguration.class));

  @Test
  void should_register_filter_right_after_micrometer_observation_filter() {
    runner.run(
        context -> {
          FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
          assertThat(registration.getFilter()).isInstanceOf(TraceIdFilter.class);
          assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 2);
          assertThat(registration.getUrlPatterns()).containsExactly("/*");
        });
  }

  @Test
  void should_not_register_filter_outside_servlet_applications() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(WebTracingAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(TraceIdFilter.class));
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner
        .withPropertyValues("codehunters.tracing.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(TraceIdFilter.class));
  }
}
