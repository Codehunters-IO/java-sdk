# sdk-tracing

W3C trace context propagation through servlet requests, async tasks and RabbitMQ, via the logging
MDC (`traceId`, `spanId`, `traceFlags`) so every log line carries the trace.

```kotlin
implementation("io.codehunters:sdk-tracing:1.0.0")
```

## What it does

| Where | Reads | Writes |
|---|---|---|
| Incoming HTTP (`TraceIdFilter`) | `traceparent`, then `x-traceparent`, then `x-trace-id` | MDC, with a new span for this service; kept for async and error dispatches |
| `@Async` / executors (`MdcTaskDecorator`) | caller's MDC | task thread's MDC |
| Published RabbitMQ messages | a `traceparent` already on the message, then the MDC, then the event envelope's `header.traceId` | `traceparent`, plus `x-traceparent`, `x-trace-id`, `x-span-id` for 0.x consumers |
| RabbitMQ listeners, including batch | `traceparent`, then `x-traceparent`, then `x-trace-id` (any header case, `byte[]` values decoded) | MDC, with a new span |

Invalid ids (wrong length, upper case, all zeros) are ignored and a new trace starts. The sampled
flag travels with the trace.

Application code reads the context through `TraceContextPort` (`currentTraceId()`,
`currentSpanId()`, `currentTraceParent()`), never through the MDC directly.

## Coexistence

- **Micrometer Tracing / OpenTelemetry:** the servlet filter runs right after Micrometer's
  observation filter. With a real tracer the MDC is already populated and the filter, the listener
  interceptor and the message post-processor leave it alone; with Boot's no-op tracer, or without
  Micrometer, this SDK provides the context. Both use the same `traceId` / `spanId` MDC keys.
- **Partial MDC:** if other code puts only a W3C `traceId` in the MDC, publishing keeps that trace
  with a new span.
- **Gateway:** matches the gateway contract (`traceparent`, with `x-traceparent` as a defensive copy).
- **sdk-event-messaging:** this module is the single owner of the outbound Rabbit post-processor;
  it is added to each `RabbitTemplate` at most once.

## Known limits

- `tracestate` is not propagated over HTTP (no vendor state is used today).
- Span ids created here are never reported to a tracing backend: in a mixed estate with an
  OpenTelemetry collector, children of these spans point at parents the backend never saw.
- Trace ids that are not W3C ids (for example UUIDs sent by an old producer as `x-trace-id`) are
  dropped and a new trace starts.
- Re-sending an inbound `Message` object (forwarding, republish recoverers) keeps its original
  `traceparent`.
- Listener containers declared directly as beans (not through a container factory) are not traced.

## Configuration

| Key | Default |
|---|---|
| `codehunters.tracing.enabled` | `true` |

RabbitMQ propagation needs `spring-rabbit` and Jackson on the classpath; the servlet filter needs a
servlet web application.

## Migrating from 0.x

| 0.x | 1.0 |
|---|---|
| `TraceContextPort.currentTraceId()` returns `String` or `null` | returns `Optional<String>`; new `currentTraceParent()` |
| Header and MDC constants on `TraceIdFilter`, `InboundTraceMethodInterceptor`, ... | `TraceHeaders` |
| Wrote and read only `x-traceparent` | W3C `traceparent` first; `x-traceparent` still read and written |
| Reused the caller's span id | New span per service; the caller's span is the parent |
| Copied any `traceId` from the event body into `traceparent` | Only valid W3C trace ids |
| Needed a `JsonMapper` bean for RabbitMQ tracing | No bean needed; Jackson on the classpath |
| Ran first, before Micrometer, generating a competing trace id | Runs after Micrometer's observation filter and defers to it |
| Error-page and async-dispatch logs had no trace | Same trace as the request |
