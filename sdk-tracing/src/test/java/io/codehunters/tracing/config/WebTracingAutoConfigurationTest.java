package io.codehunters.tracing.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.codehunters.tracing.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

class WebTracingAutoConfigurationTest {

  // micrometer-tracing is on the test classpath; hide it unless a test is about it.
  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(WebTracingAutoConfiguration.class))
          .withClassLoader(new FilteredClassLoader("io.micrometer.tracing"));

  @Test
  void should_register_filter_first_in_servlet_chain() {
    runner.run(
        context -> {
          FilterRegistrationBean<?> registration = context.getBean(FilterRegistrationBean.class);
          assertThat(registration.getFilter()).isInstanceOf(TraceIdFilter.class);
          assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
          assertThat(registration.getUrlPatterns()).containsExactly("/*");
        });
  }

  @Test
  void should_leave_tracing_to_micrometer_when_it_is_present() {
    new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(WebTracingAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(TraceIdFilter.class));
  }

  @Test
  void should_not_register_filter_outside_servlet_applications() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(WebTracingAutoConfiguration.class))
        .withClassLoader(new FilteredClassLoader("io.micrometer.tracing"))
        .run(context -> assertThat(context).doesNotHaveBean(TraceIdFilter.class));
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner
        .withPropertyValues("codehunters.tracing.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(TraceIdFilter.class));
  }
}
