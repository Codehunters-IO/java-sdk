# Changelog — sdk-tracing

## 1.0.0

### Breaking
- `TraceContextPort` returns `Optional` and adds `currentTraceParent()`.
- Header and MDC name constants move to `TraceHeaders`.
- `TracePropagationMessagePostProcessor` has a no-argument constructor;
  `RabbitTracingBeanPostProcessor` takes `ObjectProvider`s.

### Added
- W3C `traceparent` is read first and written on every published message; the sampled flag is
  propagated (`traceFlags` in the MDC).
- `TraceParent` value object shared by all components.
- `codehunters.tracing.enabled`.

### Fixed
- Only `x-traceparent` was understood, so the trace was lost with OTEL, Micrometer and the gateway.
- Each service reused its caller's span id instead of starting a child span.
- All-zero trace ids were accepted; non-W3C ids from message bodies (UUIDs) produced invalid
  `traceparent` headers; the sampled flag was always `01`.
- The servlet filter competed with Micrometer Tracing and registered outside servlet applications;
  it now runs after Micrometer's observation filter and only in servlet applications.
- Error and async dispatches logged without a trace id.
- Batch listeners and AMQP headers with another case or `byte[]` values lost the trace.
- Rabbit tracing failed to start without a `JsonMapper` bean, initialised beans early from its
  bean post-processor, and could add the outbound post-processor twice.

### Removed
- Unused `aspectjweaver` compile dependency.
