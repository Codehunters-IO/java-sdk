package io.codehunters.tracing.config;

import io.codehunters.tracing.TraceContextPort;
import io.codehunters.tracing.mdc.MdcTraceContextAdapter;
import io.codehunters.tracing.micrometer.MicrometerTraceContextAdapter;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Provides {@link TraceContextPort}: backed by the Micrometer {@link Tracer} when the application
 * has one, otherwise (no tracer bean, or Boot's no-op tracer) by the logging MDC. Runs after Boot's
 * tracing auto-configurations so their tracer is visible.
 */
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration",
      "org.springframework.boot.micrometer.tracing.autoconfigure.NoopTracerAutoConfiguration",
      "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration",
      "org.springframework.boot.micrometer.tracing.brave.autoconfigure.BraveAutoConfiguration"
    })
@ConditionalOnBooleanProperty(name = "codehunters.tracing.enabled", matchIfMissing = true)
public class TracingAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public TracingAutoConfiguration() {}

  /**
   * Provides the port backed by the application's tracer.
   *
   * @param tracer the Micrometer tracer
   * @return a {@link MicrometerTraceContextAdapter}, or an {@link MdcTraceContextAdapter} when the
   *     tracer is the no-op tracer
   */
  @Bean
  @ConditionalOnBean(Tracer.class)
  @ConditionalOnMissingBean
  public TraceContextPort traceContextPort(Tracer tracer) {
    return tracer == Tracer.NOOP
        ? new MdcTraceContextAdapter()
        : new MicrometerTraceContextAdapter(tracer);
  }

  /**
   * Provides the MDC-backed port when the application has no tracer.
   *
   * @return an {@link MdcTraceContextAdapter}
   */
  @Bean
  @ConditionalOnMissingBean
  public TraceContextPort mdcTraceContextPort() {
    return new MdcTraceContextAdapter();
  }
}
