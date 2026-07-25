package io.codehunters.tracing.config;

import io.codehunters.tracing.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/** Web tracing auto-configuration: registers the {@link TraceIdFilter} for servlet requests. */
@AutoConfiguration
@ConditionalOnClass({HttpServletRequest.class, FilterRegistrationBean.class})
public class WebTracingAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public WebTracingAutoConfiguration() {}

  /**
   * Provides the trace ID servlet filter.
   *
   * @return a {@link TraceIdFilter}
   */
  @Bean
  @ConditionalOnMissingBean
  public TraceIdFilter traceIdFilter() {
    return new TraceIdFilter();
  }

  /**
   * Registers the trace ID filter for all request paths at highest precedence.
   *
   * @param filter the trace ID filter
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
