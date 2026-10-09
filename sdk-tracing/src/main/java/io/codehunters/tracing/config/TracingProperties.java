package io.codehunters.tracing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the tracing SDK, bound from the {@code codehunters.tracing} prefix. Sampling,
 * export and the standard propagation formats stay under Spring Boot's {@code
 * management.tracing.*}.
 *
 * @param enabled whether the SDK registers its {@code TraceContextPort} and legacy header
 *     propagator
 * @param legacyHeaders the gateway's {@code x-traceparent} / {@code x-trace-id} headers
 */
@ConfigurationProperties(prefix = "codehunters.tracing")
public record TracingProperties(
    @DefaultValue("true") boolean enabled, @DefaultValue LegacyHeaders legacyHeaders) {

  /**
   * The gateway's legacy trace headers, read through an OpenTelemetry propagator.
   *
   * @param enabled whether {@code x-traceparent} / {@code x-trace-id} are read when a request or
   *     message carries no standard {@code traceparent}
   * @param inject whether {@code x-traceparent} is also written on outbound requests and messages,
   *     for consumers that read only that header
   */
  public record LegacyHeaders(
      @DefaultValue("true") boolean enabled, @DefaultValue("false") boolean inject) {}
}
