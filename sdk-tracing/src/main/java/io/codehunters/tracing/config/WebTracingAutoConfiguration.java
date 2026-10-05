package io.codehunters.tracing.config;

import io.codehunters.tracing.TraceIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Registers {@link TraceIdFilter} first in servlet applications. When Micrometer Tracing is on the
 * classpath it owns the request's trace context, so this configuration steps aside.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(FilterRegistrationBean.class)
@ConditionalOnMissingClass("io.micrometer.tracing.Tracer")
@ConditionalOnBooleanProperty(name = "codehunters.tracing.enabled", matchIfMissing = true)
public class WebTracingAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public WebTracingAutoConfiguration() {}

  /**
   * Provides the trace context servlet filter.
   *
   * @return a {@link TraceIdFilter}
   */
  @Bean
  @ConditionalOnMissingBean
  public TraceIdFilter traceIdFilter() {
    return new TraceIdFilter();
  }

  /**
   * Registers the filter for all paths at highest precedence.
   *
   * @param filter the trace context filter
   * @return the filter registration
   */
  @Bean
  @ConditionalOnMissingBean(name = "traceIdFilterRegistration")
  public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration(TraceIdFilter filter) {
    FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(filter);
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    registration.setName("traceIdFilter");
    return registration;
  }
}
