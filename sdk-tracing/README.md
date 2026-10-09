# sdk-tracing

A thin layer on top of [Micrometer Tracing](https://docs.micrometer.io/tracing/reference/). Spring
Boot creates, propagates and exports the trace; this module adds the two things Boot does not have:
reading the gateway's legacy trace headers, and a framework-free `TraceContextPort` for application
code.

```kotlin
implementation("io.codehunters:sdk-tracing:3.0.0")
// the application picks the tracer bridge, for example:
implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
```

`sdk-tracing` brings only `io.micrometer:micrometer-tracing` (the API). The bridge (OpenTelemetry,
recommended, or Brave) and the exporter are the application's choice.

## What Spring Boot does

With a bridge on the classpath, Boot propagates W3C `traceparent` (and `baggage`) in and out of:

| Where | How |
|---|---|
| Servlet and WebFlux servers | observation filter, on by default |
| `RestClient`, `RestTemplate`, `WebClient` | built from Boot's auto-configured builders |
| `@Async` and executors | a `ContextPropagatingTaskDecorator` bean (see [MIGRATION-3.0.md](MIGRATION-3.0.md)) |
| `RabbitTemplate` / Rabbit listeners | `spring.rabbitmq.template.observation-enabled`, `spring.rabbitmq.listener.{simple,direct}.observation-enabled` |
| `KafkaTemplate` / Kafka listeners | `spring.kafka.template.observation-enabled`, `spring.kafka.listener.observation-enabled` |
| `@Scheduled` methods | observed by Spring Framework, a new trace per run |

It also puts `traceId` / `spanId` in the logging MDC, samples (`management.tracing.sampling.probability`)
and exports spans (`management.otlp.tracing.*`, `management.tracing.*`). The propagation formats are
`management.tracing.propagation.type` / `.consume` / `.produce`.

## What this module adds

| Bean | Does |
|---|---|
| `LegacyTraceHeadersPropagator` | OpenTelemetry `TextMapPropagator`: reads the gateway's legacy headers when no standard `traceparent` is present |
| `TraceContextPort` | `currentTraceId()`, `currentSpanId()`, `currentTraceParent()` of the span in scope, read from the Micrometer `Tracer` |

### Legacy headers (OpenTelemetry bridge)

Boot composes every `TextMapPropagator` bean into its `ContextPropagators`, so the extra propagator
applies to every instrumented server, client, template and listener above. On extraction:

1. a valid `traceparent` (or a parent another propagator already extracted, for example B3) always
   wins: the legacy headers are ignored;
2. otherwise `x-traceparent` (W3C format, sampled flag kept);
3. otherwise `x-trace-id` with `x-span-id`, sampled;
4. otherwise `x-trace-id` alone, sampled, with a generated parent span id.

Ids that are not W3C ids (wrong length, upper case, all zeros, UUIDs) are ignored and Boot starts a
new trace. Injection stays standard W3C; set `codehunters.tracing.legacy-headers.inject=true` to
also write `x-traceparent` for consumers that read only that header. `x-trace-id` / `x-span-id` are
never written.

The propagator is registered under the same condition as Boot's own (`management.tracing.export.enabled`,
default `true`), and only when Boot's OpenTelemetry tracing auto-configuration is on the classpath.

**Brave** is not supported for legacy headers: Boot builds Brave's propagation from properties only,
with no extension point for an extra extractor. With Brave, standard W3C/B3 propagation and
`TraceContextPort` work; `x-traceparent` / `x-trace-id` are not read.

### TraceContextPort

| Application has | Port |
|---|---|
| a Micrometer `Tracer` bean (OpenTelemetry or Brave) | `MicrometerTraceContextAdapter`: the ids the tracer exports and propagates; a deferred sampling decision is reported as not sampled |
| no `Tracer` bean, or Boot's no-op tracer | `MdcTraceContextAdapter`: reads `traceId` / `spanId` / `traceFlags` from the MDC; never starts or propagates a trace |
| its own `TraceContextPort` bean | that bean |

`sdk-event-messaging-core` and `sdk-web` read the port to stamp the trace id on events and error
responses.

## Configuration

| Key | Default | |
|---|---|---|
| `codehunters.tracing.enabled` | `true` | registers the port and the propagator |
| `codehunters.tracing.legacy-headers.enabled` | `true` | reads `x-traceparent` / `x-trace-id` |
| `codehunters.tracing.legacy-headers.inject` | `false` | also writes `x-traceparent` |

## Known limits

- A bare `x-trace-id` has no parent span: the generated parent id is never reported, so the backend
  shows the first span of this service under a parent it never saw.
- `tracestate` is not read from `x-traceparent` (the gateway sends none).

## Packages

| Package | Contents |
|---|---|
| `io.codehunters.tracing` | `TraceContextPort`, `TraceParent`, `TraceHeaders` |
| `io.codehunters.tracing.otel` | `LegacyTraceHeadersPropagator` |
| `io.codehunters.tracing.micrometer` | `MicrometerTraceContextAdapter` |
| `io.codehunters.tracing.mdc` | `MdcTraceContextAdapter` (fallback) |
| `io.codehunters.tracing.config` | Auto-configuration and `TracingProperties` |

## Migrating

- From 2.x: [MIGRATION-3.0.md](MIGRATION-3.0.md) — the servlet filter, task decorator and RabbitMQ
  components are removed in favour of Boot's instrumentation.
- From 1.x: [MIGRATION-2.0.md](MIGRATION-2.0.md) — package moves.
