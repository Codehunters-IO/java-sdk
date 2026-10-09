/**
 * OpenTelemetry bridge support: {@link io.codehunters.tracing.otel.LegacyTraceHeadersPropagator}
 * reads the gateway's {@code x-traceparent} / {@code x-trace-id} headers next to Boot's standard
 * propagators.
 */
package io.codehunters.tracing.otel;
