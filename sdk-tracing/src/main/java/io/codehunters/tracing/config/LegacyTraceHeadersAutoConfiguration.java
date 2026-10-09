package io.codehunters.tracing.config;

import io.codehunters.tracing.otel.LegacyTraceHeadersPropagator;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.micrometer.tracing.autoconfigure.ConditionalOnEnabledTracingExport;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Registers {@link LegacyTraceHeadersPropagator} with Spring Boot's OpenTelemetry tracing. Boot
 * composes every {@link TextMapPropagator} bean into its {@code ContextPropagators}, which the
 * Micrometer {@code Propagator} and so every instrumented server, client and messaging template
 * use; this bean is added last, after the formats of {@code management.tracing.propagation.*}, and
 * under the same condition as Boot's own propagators ({@code management.tracing.export.enabled}).
 */
@AutoConfiguration(afterName = LegacyTraceHeadersAutoConfiguration.BOOT_OTEL_TRACING)
@ConditionalOnClass(
    value = TextMapPropagator.class,
    name = LegacyTraceHeadersAutoConfiguration.BOOT_OTEL_TRACING)
@ConditionalOnBooleanProperty(name = "codehunters.tracing.enabled", matchIfMissing = true)
@ConditionalOnBooleanProperty(
    name = "codehunters.tracing.legacy-headers.enabled",
    matchIfMissing = true)
@EnableConfigurationProperties(TracingProperties.class)
public class LegacyTraceHeadersAutoConfiguration {

  static final String BOOT_OTEL_TRACING =
      "org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure."
          + "OpenTelemetryTracingAutoConfiguration";

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
  @ConditionalOnEnabledTracingExport
  @ConditionalOnMissingBean
  public LegacyTraceHeadersPropagator legacyTraceHeadersPropagator(TracingProperties properties) {
    return new LegacyTraceHeadersPropagator(properties.legacyHeaders().inject());
  }
}
