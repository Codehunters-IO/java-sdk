# Changelog — sdk-event-messaging-kafka

## 9.3.0

Consumer side of the envelope, in the new `io.codehunters.messaging.kafka.consumer` package.

### Added
- `EnvelopeReader#read(ConsumerRecord<String, String>, Class<T>)`: reads the `EventMessage`
  envelope with the shared `EnvelopeFormat` pin and unknown fields ignored, whatever the
  application mapper sets.
- `ReceivedEnvelope<T>`: the envelope with the `outbox-message-id`, `traceparent` and retry
  attempt record headers; `eventId()` falls back to `outbox-message-id` for envelopes written
  before 9.3.
- `UnreadableEnvelopeException`: a fixed message, no cause and only the parser class name, so the
  record value never reaches a log or a header. Never retried.
- `InboundRetry` and `InboundRetryTopics#configuration(InboundRetry)`: a non-blocking retry
  topology for one topic, `<topic>-retry-0..N` then `<topic>.DLT`, with the exact delays given and
  a list of exceptions sent straight to the dead-letter topic. Topics are never auto-created.
- `SanitizedDeadLetters`, auto-configured as the `RetryTopicComponentFactory`: on every hop the
  exception headers (`kafka_exception-fqcn`, `kafka_exception-cause-fqcn`) carry class names only,
  the message and stack-trace headers of both families (`kafka_exception-*`, `kafka_dlt-exception-*`)
  are never written, `event-id` and `trace-id` headers are added, and one WARN per dead-lettered
  record names its topic, partition, offset and failure class.
- `DeadLetterHook` and `DeadLetter`: called on the consumer thread before a record is published to
  a topic ending in `.DLT`, never for a retry hop. A hook that throws makes the record redelivered,
  so hooks run at least once.
- Beans `inboundEnvelopeJsonMapper` (not a default candidate), `envelopeReader`,
  `retryTopicComponentFactory`, `retryTopicSchedulerWrapper`, `inboundForwardingProducerFactory`
  and `inboundForwardingKafkaTemplate` (neither a default candidate) and `inboundRetryTopics`.
- `codehunters.messaging.consumer.enabled` (default `true`): `false` registers none of them.

## 9.2.0

### Changed
- Released with `sdk-event-messaging-core` 9.2.0, so the messaging modules keep one version. No
  change in this module.

## 9.1.0

### Changed
- Released with `sdk-event-messaging-core` 9.1.0, so the messaging modules keep one version. No
  change in this module.

## 9.0.0

Versioned with `-core` and `-jdbc` 9.0.0, which support PostgreSQL only, drop the old `outbox.*`
keys and store the request's trace context with each row. Migration guide:
[MIGRATION-9.0.md](../MIGRATION-9.0.md).

### Removed
- Lombok from the build.

### Changed
- The adapter reads `OutboxMessage` through record accessors (`topic()`, `key()`, `message()`,
  `id()`, `traceparent()`); custom publishers written against the 8.x getters must do the same.

### Added
- `KafkaMessagePublisherAdapter(…, boolean legacyTraceparentHeader)`, fed by
  `codehunters.outbox.publisher.legacy-traceparent-header` (default `false`).

### Fixed
- Outbox records carried no trace context at all. The adapter now adds the `traceparent`
  record header from the context stored with the row.

### Behaviour changes
- Every outbox record with a stored context carries a `traceparent` header, unchanged;
  `x-traceparent` only when the switch is on. A stored value that is not a valid `W3cTraceParent`
  (malformed, version `ff`, longer than 55 characters) is not sent in either header.
- With `-jdbc` 9.0.0, records are sent right after their transaction commits, and the outbox no
  longer turns on application-wide scheduling (`@EnableScheduling`).

## 8.0.0

Migration guide: [MIGRATION-8.0.md](../MIGRATION-8.0.md).

### Breaking
- Packages reorganised by capability: `KafkaMessagePublisherAdapter` moves to
  `io.codehunters.messaging.kafka` and the auto-configuration to
  `io.codehunters.messaging.kafka.config`. Only imports change.

## 7.0.0

No changes of its own: versioned with `-core` and `-jdbc` 7.0.0, which charge reclaim attempts only
for rows whose send had started. Migration guide: [MIGRATION-7.0.md](../MIGRATION-7.0.md).

## 6.0.0

First release; the Kafka publisher of `sdk-event-messaging` 5.1.5. Migration guide:
[MIGRATION-6.0.md](../MIGRATION-6.0.md).

### Breaking
- Replaces `sdk-event-messaging` together with `-jdbc`.
- Requires a `ProducerFactory` bean instead of a `KafkaTemplate` bean, and no longer sends through
  the application's `KafkaTemplate`.
- `codehunters.outbox.publisher.kafka.send-timeout` defaults to `5s` (was `10s`).

### Added
- Dedicated `outboxKafkaProducerFactory` and `outboxKafkaTemplate` (not default candidates),
  derived from the application's producer configuration with String serializers, `acks=all`,
  `enable.idempotence=true` and no transactional id.
- Records keyed by the outbox key (the aggregate) with an `outbox-message-id` header.
- Selected automatically when it is the only transport artifact present.

### Fixed
- An application JSON value serializer double-encoded outbox payloads.
- A publish could block far beyond `send-timeout`: `send()` waited up to `max.block.ms` (60 s) for
  metadata or buffer space before the acknowledgement wait began. The outbox producer now sets
  `max.block.ms`, `delivery.timeout.ms` and `request.timeout.ms` to `send-timeout` and `linger.ms`
  to `0`, and the acknowledgement wait gets only what is left of `send-timeout`.
- An application producer setting idempotence rejects (`max.in.flight.requests.per.connection` > 5,
  `retries=0`) made every outbox send fail; the outbox producer caps the first at 5 and resets the
  second. The derived configuration is parsed at startup, so other invalid values fail startup
  instead of the first dispatch.
- The outbox factory skipped Boot's `DefaultKafkaProducerFactoryCustomizer` beans (no Micrometer
  producer metrics); they are now applied, with the forced settings re-applied afterwards.
- Several `ProducerFactory` beans without a primary failed with an unclear injection error, and a
  factory without exposed configuration produced a producer with no bootstrap servers; both now fail
  startup with a message naming `outboxKafkaProducerFactory`.
- The outbox producer reused the application's `client.id`, colliding in JMX and broker quotas; it
  now uses `<client.id>-outbox`, or `outbox-<spring.application.name>`.
