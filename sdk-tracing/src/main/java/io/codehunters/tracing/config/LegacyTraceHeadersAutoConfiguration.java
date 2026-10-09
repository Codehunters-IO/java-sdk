package io.codehunters.tracing.config;

import io.codehunters.tracing.otel.LegacyTraceHeadersPropagator;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Registers {@link LegacyTraceHeadersPropagator} with the OpenTelemetry bridge. Spring Boot
 * composes every {@link TextMapPropagator} bean into its {@code ContextPropagators}, which the
 * Micrometer {@code Propagator} and so every instrumented server, client and messaging template
 * use; this bean is added last, after the formats of {@code management.tracing.propagation.*}.
 */
@AutoConfiguration(
    afterName =
        "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration")
@ConditionalOnClass(TextMapPropagator.class)
@ConditionalOnBooleanProperty(name = "codehunters.tracing.enabled", matchIfMissing = true)
@ConditionalOnBooleanProperty(
    name = "codehunters.tracing.legacy-headers.enabled",
    matchIfMissing = true)
@EnableConfigurationProperties(TracingProperties.class)
public class LegacyTraceHeadersAutoConfiguration {

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public LegacyTraceHeadersAutoConfiguration() {}

  /**
   * Provides the legacy header propagator.
   *
   * @param properties the tracing configuration
   * @return a {@link LegacyTraceHeadersPropagator}
   */
  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  @ConditionalOnMissingBean
  public LegacyTraceHeadersPropagator legacyTraceHeadersPropagator(TracingProperties properties) {
    return new LegacyTraceHeadersPropagator(properties.legacyHeaders().inject());
  }
}
