# 0022 — sdk-tracing on top of Micrometer Tracing

- Status: accepted
- Date: 2026-10-09

## Context

`sdk-tracing` 2.x parsed W3C `traceparent` itself, generated span ids into the MDC, wrapped
`@Async` tasks and instrumented every `RabbitTemplate` and listener. Spring Boot 4 already does all of
that through Micrometer Tracing and observations (servlet and WebFlux, `RestClient`/`WebClient`,
`@Async` with `ContextPropagatingTaskDecorator`, `@Scheduled`, Rabbit and Kafka templates and
listeners), and it exports the spans. The SDK's spans were never exported, it had no outbound HTTP
propagation, no WebFlux support, and its MDC trace could disagree with the real tracer's.

The only things the platform does not provide are reading the gateway's legacy headers
(`x-traceparent`, `x-trace-id`, `x-span-id`) and a small `TraceContextPort` other SDKs use.

## Decision

- `sdk-tracing` 3.0 depends on `io.micrometer:micrometer-tracing` (API only); the application picks
  the bridge.
- It contributes `LegacyTraceHeadersPropagator`, an OpenTelemetry `TextMapPropagator` that Boot
  composes with its own W3C/B3 propagators. It extracts from the legacy headers only when no
  standard context is present; it injects `x-traceparent` only when
  `codehunters.tracing.legacy-headers.inject=true`. Brave is not supported for legacy headers.
- `x-traceparent` keeps the flags it carries. A legacy `x-trace-id` (with or without `x-span-id`)
  carries no sampling decision, so it is sampled by trace-id ratio with
  `management.tracing.sampling.probability` (0.1 when Boot's properties are absent) — the decision
  Boot's root sampler would make — instead of always.
- `TraceContextPort` is backed by the Micrometer `Tracer`; the MDC adapter remains only when there is
  no tracer (or only `Tracer.NOOP`). It reports a trace as sampled only when the MDC carries trace
  flags that say so. The SDK no longer writes the MDC, so without a tracer bridge the trace id is
  empty in logs, problem responses and events.
- The servlet filter, the task decorator and the RabbitMQ post-processor and interceptors are
  removed; `MIGRATION-3.0.md` maps each one to the Boot property or bean that replaces it.

## Consequences

- Good: one source of truth for the current trace; spans are exported; outbound HTTP and WebFlux are
  covered by Boot; the module shrinks to two small types.
- Bad: services must add a tracing bridge and turn on Boot's observation properties for Rabbit and
  Kafka; legacy-header extraction needs the OpenTelemetry bridge.
- The outbox no longer relies on this module to stamp its messages; see
  [ADR 0024](0024-outbox-carries-its-own-trace-context.md).
