# 0024 — The outbox carries its own trace context

- Status: accepted
- Date: 2026-10-09

## Context

Outbox rows are written during a request and published later by a scheduled dispatch. Boot 4
observes `@Scheduled` runs, so at publish time the current trace is the scheduler's, not the
request's. The 8.x outbox stored only the trace id in the envelope and relied on `sdk-tracing`'s Rabbit
post-processor, which preferred the scheduler's MDC; Kafka records got no `traceparent` at all.
`sdk-tracing` 3.0 drops that post-processor ([ADR 0022](0022-tracing-on-micrometer-tracing.md)).

## Decision

- The full W3C `traceparent` of the writing request is stored with the row (nullable column
  `traceparent`, captured when the row is built from `TraceContextPort` when `sdk-tracing` is present).
- Every value is checked once with the shared W3C check (`W3cTraceParent`: 55 characters, lowercase
  hex, version not `ff`, non-zero ids) before it is stored or sent. An invalid value is stored as
  `NULL` (never failing the business transaction) and no header is written for it; the caller's trace
  id must equal the current trace id exactly.
- Both transports write the stored value as the `traceparent` header on publish (`x-traceparent`
  only with `codehunters.outbox.publisher.legacy-traceparent-header=true`). The SDK-owned outbox
  templates keep Boot's observation off, so nothing overwrites it.
- When a Micrometer `Tracer` exists, the stored context is made current around each publish, so logs
  and any span started inside it belong to the request's trace; the dispatcher's context is restored
  afterwards. Without a tracer the stored value is sent unchanged. Without a tracer bridge nothing is
  captured, because `sdk-tracing` 3.0 no longer writes the MDC.

## Consequences

- Good: consumers continue the request's trace on RabbitMQ and Kafka, independent of when the row is
  dispatched.
- Bad: one more column (`postgresql/outbox_message_add_traceparent.sql` must run before deploying);
  no producer span is exported for outbox sends.
