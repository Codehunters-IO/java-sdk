package io.codehunters.tracing.config;

import io.codehunters.tracing.MdcTaskDecorator;
import io.codehunters.tracing.MdcTraceContextAdapter;
import io.codehunters.tracing.TraceContextPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Core tracing auto-configuration: the MDC-backed trace context port and task decorator. */
@AutoConfiguration
public class TracingAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public TracingAutoConfiguration() {}

  /**
   * Provides the default MDC-backed trace context port.
   *
   * @return a {@link TraceContextPort}
   */
  @Bean
  @ConditionalOnMissingBean
  public TraceContextPort traceContextPort() {
    return new MdcTraceContextAdapter();
  }

  /**
   * Provides a task decorator that propagates MDC across threads.
   *
   * @return an {@link MdcTaskDecorator}
   */
  @Bean
  @ConditionalOnMissingBean
  public MdcTaskDecorator mdcTaskDecorator() {
    return new MdcTaskDecorator();
  }
}
