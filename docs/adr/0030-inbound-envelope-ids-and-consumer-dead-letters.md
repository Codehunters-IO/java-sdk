# 0030 — Envelope event ids and SDK-owned consumer dead letters

- Status: accepted
- Date: 2026-10-27

## Context

The outbox delivers at least once, and consumers were told to deduplicate on the
`outbox-message-id` record header. That id lives outside the envelope: it is lost as soon as the
envelope is stored, forwarded or replayed by anything that does not copy Kafka headers, and the
envelope had no place for a business deduplication key chosen by the producer (for example a
command id, so the same request sent twice is recognised as one).

Services are also starting to consume commands from Kafka, not only to publish events. Each one
would otherwise rebuild the same pieces: reading the envelope with the right Jackson settings,
continuing the producer's trace, retrying without stalling a partition, and dead-lettering. Spring
Kafka's default dead-letter recoverer writes the exception message and full stack trace into
record headers (`kafka_dlt-exception-message`, `kafka_dlt-exception-stacktrace`, and
`kafka_exception-*` on retry topics). Listener exception messages routinely quote input values, so
those headers copy personal data into a topic with its own, usually longer, retention, and the
default recoverer also logs the exception.

## Decision

- `EventMessage.Header` gains two components, appended at the end: `eventId` (a `String`) and
  `idempotencyKey`. `OutboxEventFactory` stamps the outbox row id as `eventId`, so it equals
  `outbox-message-id`; `EventMessageFactory#create(event)` stamps a random UUID, and
  `create(event, eventId)` takes a given one. `idempotencyKey` is optional, set with
  `OutboxEvent#idempotencyKey(String)`, and a blank key is rejected. Compatibility constructors keep
  the 9.2 signatures of `Header` and `OutboxEvent`.
- Both fields are always written, as `null` when absent. The Jackson pin of the envelope becomes
  the public `io.codehunters.messaging.json.EnvelopeFormat` in `-core`, shared by the `-jdbc` writer
  and the `-kafka` reader. Readers ignore unknown fields and fall back to `outbox-message-id` when
  `eventId` is null.
- The release is additive: `sdk-event-messaging-*` 9.3.0 and `sdk-bom` 7.3.0.
- The consumer side ships in `sdk-event-messaging-kafka` (`io.codehunters.messaging.kafka.consumer`),
  auto-configured unless `codehunters.messaging.consumer.enabled=false`:
  - `EnvelopeReader` returns a `ReceivedEnvelope` with `eventId`, `idempotencyKey`, `traceparent`
    and the retry attempt. Failures raise `UnreadableEnvelopeException`, which carries a fixed
    message and the parser class name only, and is never retried.
  - `InboundRetryTopics` builds non-blocking retry topics per topic: `<topic>-retry-0..N` by index
    with the exact delays given, then `<topic>.DLT`. The SDK never creates topics; the operator
    provisions them with the main topic's partition count. Failed records are forwarded byte for
    byte by an idempotent producer derived from the application's configuration.
  - `SanitizedDeadLetters` replaces Spring's `RetryTopicComponentFactory`. On every hop the
    exception headers carry the listener's exception class and root cause class only; message and
    stack-trace headers of both families are never written; `event-id` and `trace-id` headers are
    added; the listener exception is never logged, and one WARN per dead-lettered record names its
    position and failure class.
  - `DeadLetterHook` beans run on the consumer thread before a record is published to a topic
    ending in `.DLT`, never for a retry hop. A hook that throws fails the recovery, so the record is
    redelivered and no record reaches the dead-letter topic without its hooks having run.

## Consequences

- Good: the deduplication id travels inside the envelope and survives storage and replay; old
  envelopes still deduplicate through the `outbox-message-id` fallback.
- Good: producers can name the business request, so consumers recognise a resent request as the
  same one, not a new event.
- Good: one reader, one retry topology and one dead-letter format for every consumer; a failing
  record never stalls its partition, and the trace continues across retry hops.
- Good: exception messages and stack traces never reach a dead-letter topic or a log, so personal
  data quoted by a listener exception is not copied into a long-retention topic.
- Good: additive on the wire and in the API, so it ships as a minor version.
- Bad: hooks are at least once. A hook whose side effect succeeded may run again after a later
  failure in the same recovery, so duplicate hook side effects are possible; hooks must tolerate
  them (key them on the event id).
- Bad: stack traces are deliberately absent from dead-letter headers. This diverges from the
  messaging guideline (§6.4), which asks dead letters to keep the exception class, the stack trace
  and the attempt count: the class names and attempt count are kept, the stack trace is not, for
  PII reasons. Diagnosing a dead letter means correlating `trace-id` / `event-id` with the
  service's own logs and traces.
- Bad: retry and dead-letter topics must be provisioned by the operator, with the right partition
  count, before the listener starts; a missing topic fails the forward instead of creating it.
- Bad: every consumer must ignore unknown envelope fields; one that binds the envelope with
  `FAIL_ON_UNKNOWN_PROPERTIES` enabled breaks on 9.3 envelopes.
- Bad: `SanitizedDeadLetters` applies to every retry-topic configuration of the application; one
  that needs Spring's default headers must define its own `RetryTopicComponentFactory`, which
  disables the sanitizing and the hooks.
