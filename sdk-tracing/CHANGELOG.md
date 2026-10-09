# Changelog — sdk-tracing

## 3.0.0

The module is now a thin layer on top of Micrometer Tracing; Spring Boot propagates and exports the
trace. See [MIGRATION-3.0.md](MIGRATION-3.0.md).

### Removed
- `TraceIdFilter` and `WebTracingAutoConfiguration`: Boot's server observation filter (servlet and
  WebFlux).
- MDC span generation and `internal.TraceMdc`: the tracer bridge fills the MDC.
- `MdcTaskDecorator`: use a `ContextPropagatingTaskDecorator` bean.
- The `amqp` package (`TracePropagationMessagePostProcessor`, `InboundTraceMethodInterceptor`,
  `RabbitTracingBeanPostProcessor`) and `RabbitTracingAutoConfiguration`: use
  `spring.rabbitmq.template.observation-enabled` and
  `spring.rabbitmq.listener.{simple,direct}.observation-enabled`.
- Compile-time dependencies on `spring-web`, `spring-rabbit`, the servlet API and Jackson.

### Changed
- Depends on `io.micrometer:micrometer-tracing` (API only); the application chooses the bridge.
- `TraceContextPort` is backed by the Micrometer `Tracer` (`MicrometerTraceContextAdapter`) when a
  tracer bean exists; `MdcTraceContextAdapter` is only the fallback without one, or with Boot's
  no-op tracer. `TraceContextPort`, `TraceParent` and `TraceHeaders` keep their signatures.
- `TracingAutoConfiguration` runs after Boot's tracing auto-configurations.
- `codehunters.tracing.*` is bound to `TracingProperties`; its metadata is generated.

### Added
- `LegacyTraceHeadersPropagator` (`io.codehunters.tracing.otel`): an OpenTelemetry
  `TextMapPropagator` that Boot composes with its own; it reads `x-traceparent`, then `x-trace-id`
  (+ `x-span-id`) when no valid `traceparent` is present, and optionally writes `x-traceparent`.
- `LegacyTraceHeadersAutoConfiguration`, active with Boot's OpenTelemetry tracing and
  `management.tracing.export.enabled`.
- `codehunters.tracing.legacy-headers.enabled` (default `true`) and
  `codehunters.tracing.legacy-headers.inject` (default `false`).

### Behaviour changes
- Without a tracer bridge no trace is started; 2.x generated one per request.
- Spans are exported and sampled by Boot (`management.tracing.sampling.probability`, default
  `0.1`); 2.x flagged every trace as sampled and exported nothing.
- Outbound HTTP through Boot's client builders and WebFlux are now propagated; Kafka is propagated
  once its observation properties are on.
- `x-traceparent`, `x-trace-id` and `x-span-id` are no longer written unless
  `codehunters.tracing.legacy-headers.inject=true` (which writes `x-traceparent` only).
- Legacy headers are read only with the OpenTelemetry bridge; with Brave they are ignored.
- `traceFlags` is no longer put in the MDC.

## 2.0.0

### Breaking
- Packages reorganised by capability: `TraceIdFilter` → `io.codehunters.tracing.servlet`,
  `MdcTaskDecorator` and `MdcTraceContextAdapter` → `io.codehunters.tracing.mdc`, the RabbitMQ
  components → `io.codehunters.tracing.amqp`. `TraceContextPort`, `TraceParent`, `TraceHeaders` and
  the auto-configurations keep their packages. Only imports change; see
  [MIGRATION-2.0.md](MIGRATION-2.0.md).

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
