package io.codehunters.tracing.config;

import io.codehunters.tracing.otel.LegacyTraceHeadersPropagator;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.springframework.beans.factory.ObjectProvider;
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
 *
 * <p>Legacy trace ids without flags are sampled at Boot's {@code
 * management.tracing.sampling.probability}, or at {@code 0.1}, Boot's default, when Boot's tracing
 * properties are not registered.
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

  static final double DEFAULT_SAMPLING_PROBABILITY = 0.1;

  /** Creates the auto-configuration. Instantiated by the Spring container. */
  public LegacyTraceHeadersAutoConfiguration() {}

  /**
   * Provides the legacy header propagator.
   *
   * @param properties the tracing configuration
   * @param bootProperties Boot's {@code management.tracing.*} configuration, when registered
   * @return a {@link LegacyTraceHeadersPropagator}
   */
  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  @ConditionalOnEnabledTracingExport
  @ConditionalOnMissingBean
  public LegacyTraceHeadersPropagator legacyTraceHeadersPropagator(
      TracingProperties properties,
      ObjectProvider<org.springframework.boot.micrometer.tracing.autoconfigure.TracingProperties>
          bootProperties) {
    return new LegacyTraceHeadersPropagator(
        properties.legacyHeaders().inject(), samplingProbability(bootProperties));
  }

  private static double samplingProbability(
      ObjectProvider<org.springframework.boot.micrometer.tracing.autoconfigure.TracingProperties>
          bootProperties) {
    return bootProperties.stream()
        .findFirst()
        .map(boot -> (double) boot.getSampling().getProbability())
        .orElse(DEFAULT_SAMPLING_PROBABILITY);
  }
}
