# sdk-tracing

W3C trace context propagation through servlet requests, async tasks and RabbitMQ, via the logging
MDC (`traceId`, `spanId`, `traceFlags`) so every log line carries the trace.

```kotlin
implementation("io.codehunters:sdk-tracing:1.0.0")
```

## What it does

| Where | Reads | Writes |
|---|---|---|
| Incoming HTTP (`TraceIdFilter`) | `traceparent`, then `x-traceparent`, then `x-trace-id` | MDC, with a new span for this service |
| `@Async` / executors (`MdcTaskDecorator`) | caller's MDC | task thread's MDC |
| Published RabbitMQ messages | a `traceparent` already on the message, then the MDC, then the event envelope's `header.traceId` | `traceparent`, plus `x-traceparent`, `x-trace-id`, `x-span-id` for 0.x consumers |
| RabbitMQ listeners | `traceparent`, then `x-traceparent`, then `x-trace-id` | MDC, with a new span |

Invalid ids (wrong length, upper case, all zeros) are ignored and a new trace starts. The sampled
flag travels with the trace.

Application code reads the context through `TraceContextPort` (`currentTraceId()`,
`currentSpanId()`, `currentTraceParent()`), never through the MDC directly.

## Coexistence

- **Micrometer Tracing / OpenTelemetry:** when `io.micrometer.tracing.Tracer` is on the classpath,
  the servlet filter is not registered and Micrometer owns the request's context. The filter, the
  listener interceptor and the message post-processor also leave an MDC that something else
  already populated alone. Both use the same `traceId` / `spanId` MDC keys.
- **Gateway:** matches the gateway contract (`traceparent`, with `x-traceparent` as a defensive copy).
- **sdk-event-messaging:** this module is the single owner of the outbound Rabbit post-processor;
  it is added to each `RabbitTemplate` at most once.

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
| Ran next to Micrometer Tracing, generating a competing trace id | Steps aside when Micrometer Tracing is present |
