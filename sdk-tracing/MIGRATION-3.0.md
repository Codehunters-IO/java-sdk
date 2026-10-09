# Migrating sdk-tracing to 3.0

3.0 stops duplicating Micrometer Tracing. Up to 2.x this module generated its own span ids in the
MDC and copied them through servlet requests, executors and RabbitMQ; those spans were never
exported, outbound HTTP was not propagated, WebFlux was not covered and the MDC ids could diverge
from the real tracer's. Spring Boot now does all of it, and this module keeps only what Boot lacks:
the gateway's legacy headers and `TraceContextPort`.

The stable API is unchanged: `TraceContextPort` (`currentTraceId()`, `currentSpanId()`,
`currentTraceParent()`), `TraceParent` and `TraceHeaders`. `TracingAutoConfiguration` keeps its name.

## 1. Add a tracer bridge

Without a bridge there is no trace at all any more (2.x invented one in the servlet filter). The
SDK no longer writes `traceId` / `spanId` to the MDC either, so without a bridge the trace id is
**empty** everywhere this platform reads it: log lines, the `traceId` of problem responses
(`sdk-web`) and the `traceId` of published events (`sdk-event-messaging-core`), unless the
application fills the MDC itself.

```kotlin
implementation("io.codehunters:sdk-tracing:3.0.0")
implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
```

Then configure Boot, not this module: `management.tracing.sampling.probability` (Boot's default is
`0.1`; 2.x marked every trace as sampled), export under `management.otlp.tracing.*`, propagation
formats under `management.tracing.propagation.*`.

## 2. Replace the removed components

| Removed in 3.0 | Use instead |
|---|---|
| `io.codehunters.tracing.servlet.TraceIdFilter` and `WebTracingAutoConfiguration` | Boot's server observation filter (servlet and WebFlux), on by default. Legacy `x-traceparent` / `x-trace-id` are read by `LegacyTraceHeadersPropagator` |
| MDC span generation (`traceId` / `spanId` / `traceFlags` written by the SDK) | the tracer bridge writes `traceId` / `spanId` to the MDC; `traceFlags` is no longer written |
| `io.codehunters.tracing.mdc.MdcTaskDecorator` | a `ContextPropagatingTaskDecorator` bean (below) |
| `io.codehunters.tracing.amqp.TracePropagationMessagePostProcessor` | `spring.rabbitmq.template.observation-enabled=true` |
| `io.codehunters.tracing.amqp.InboundTraceMethodInterceptor` | `spring.rabbitmq.listener.simple.observation-enabled=true` (or `.direct.`) |
| `io.codehunters.tracing.amqp.RabbitTracingBeanPostProcessor` and `RabbitTracingAutoConfiguration` | the two properties above |
| `io.codehunters.tracing.internal.TraceMdc` | not API; gone |
| — | Kafka, previously not instrumented: `spring.kafka.template.observation-enabled=true`, `spring.kafka.listener.observation-enabled=true` |

`@Async` and Boot's task executors:

```java
@Bean
TaskDecorator contextPropagatingTaskDecorator() {
  return new ContextPropagatingTaskDecorator(); // org.springframework.core.task.support
}
```

Boot applies a single `TaskDecorator` bean to the executors it auto-configures. It carries what is
registered in the context-propagation `ContextRegistry`: the current observation and span, and with
them the `traceId` / `spanId` MDC keys and baggage the bridge writes. It does **not** copy other MDC
keys (`tenantId`, `userId`, ...) that `MdcTaskDecorator` used to copy, unless a
`Slf4jThreadLocalAccessor` (`io.micrometer.context.integration`, in `io.micrometer:context-propagation`,
which the bridge already brings) is registered for them:

```java
@Bean
TaskDecorator contextPropagatingTaskDecorator() {
  ContextRegistry.getInstance() // io.micrometer.context
      .registerThreadLocalAccessor(new Slf4jThreadLocalAccessor("tenantId", "userId"));
  return new ContextPropagatingTaskDecorator();
}
```

List the keys: with no arguments the accessor copies the whole MDC map, `traceId` / `spanId`
included, which the span scope already restores. Register it once, before the first task runs; the
registry is global to the JVM.

Outbound HTTP needs the clients built from Boot's `RestClient.Builder`, `RestTemplateBuilder` or
`WebClient.Builder`; a client created with `RestClient.create()` is not instrumented.

The transactional outbox relays no longer receive headers from a post-processor of this module; the
messaging modules write the trace headers of each event themselves (see their changelogs).

## 3. Properties

| Key | 3.0 |
|---|---|
| `codehunters.tracing.enabled` | unchanged; now covers the port and the legacy propagator |
| `codehunters.tracing.legacy-headers.enabled` | new, default `true` |
| `codehunters.tracing.legacy-headers.inject` | new, default `false`; `true` also writes `x-traceparent` |

## Behaviour changes

- `x-traceparent`, `x-trace-id` and `x-span-id` are no longer written by default; only standard
  `traceparent` is. Consumers that still read only `x-traceparent` need
  `codehunters.tracing.legacy-headers.inject=true`.
- Legacy headers are read only with the OpenTelemetry bridge; Brave is not supported for them.
- `TraceContextPort` reports the tracer's real ids, sampled flag included; a deferred sampling
  decision is reported as not sampled. It falls back to the MDC only when there is no tracer bean
  or the tracer is Boot's no-op tracer.
- Spans are exported, so they appear in the tracing backend and count against its quota.
- A parent read from `x-trace-id` (with or without `x-span-id`) carries no sampled flag, and is
  sampled by trace-id ratio at `management.tracing.sampling.probability` (default `0.1`), the same
  decision Boot's root sampler makes; 2.x marked it sampled, which with Boot's parent-based sampler
  would record all gateway traffic. `x-traceparent` keeps the flag the gateway sent.
- The MDC fallback (`MdcTraceContextAdapter`) reports a trace as sampled only when the MDC holds a
  `traceFlags` key with the sampled bit set; without it (this SDK no longer writes the key) the
  context is reported as not sampled, so the `traceparent` stored with outbox events is `-00`.
- Without a tracer bridge, `traceId` in logs, problem responses and events is empty (see step 1).

## Logging

Logger categories under `io.codehunters.tracing.servlet`, `.amqp` and `.internal` no longer exist;
remove levels set on them.
