package io.codehunters.tracing.config;

import io.codehunters.tracing.MdcTaskDecorator;
import io.codehunters.tracing.MdcTraceContextAdapter;
import io.codehunters.tracing.TraceContextPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Core tracing beans: the MDC-backed {@link TraceContextPort} and the MDC task decorator. */
@AutoConfiguration
@ConditionalOnBooleanProperty(name = "codehunters.tracing.enabled", matchIfMissing = true)
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
   * Provides a task decorator that propagates the MDC to async tasks; Spring Boot applies a single
   * {@code TaskDecorator} bean to its task executors.
   *
   * @return an {@link MdcTaskDecorator}
   */
  @Bean
  @ConditionalOnMissingBean
  public MdcTaskDecorator mdcTaskDecorator() {
    return new MdcTaskDecorator();
  }
}
