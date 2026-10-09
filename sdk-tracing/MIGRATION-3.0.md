# Migrating sdk-tracing to 3.0

3.0 stops duplicating Micrometer Tracing. Up to 2.x this module generated its own span ids in the
MDC and copied them through servlet requests, executors and RabbitMQ; those spans were never
exported, outbound HTTP was not propagated, WebFlux was not covered and the MDC ids could diverge
from the real tracer's. Spring Boot now does all of it, and this module keeps only what Boot lacks:
the gateway's legacy headers and `TraceContextPort`.

The stable API is unchanged: `TraceContextPort` (`currentTraceId()`, `currentSpanId()`,
`currentTraceParent()`), `TraceParent` and `TraceHeaders`. `TracingAutoConfiguration` keeps its name.

## 1. Add a tracer bridge

Without a bridge there is no trace at all any more (2.x invented one in the servlet filter).

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

Boot applies a single `TaskDecorator` bean to the executors it auto-configures. It carries the
whole Micrometer context (span, MDC, baggage), not just a copy of the MDC.

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

## Logging

Logger categories under `io.codehunters.tracing.servlet`, `.amqp` and `.internal` no longer exist;
remove levels set on them.
