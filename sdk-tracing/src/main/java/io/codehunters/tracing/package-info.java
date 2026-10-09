/**
 * Tracing SDK public API, on top of Micrometer Tracing: the W3C {@link
 * io.codehunters.tracing.TraceParent}, the header and MDC key names in {@link
 * io.codehunters.tracing.TraceHeaders}, and {@link io.codehunters.tracing.TraceContextPort} to read
 * the current trace. Propagation is Spring Boot's; {@code otel} adds the gateway's legacy headers.
 */
package io.codehunters.tracing;
