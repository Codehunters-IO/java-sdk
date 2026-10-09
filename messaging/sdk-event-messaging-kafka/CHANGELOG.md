# Changelog — sdk-event-messaging-kafka

## 9.0.0

Versioned with `-core` and `-jdbc` 9.0.0, which support PostgreSQL only, drop the old `outbox.*`
keys and store the request's trace context with each row. Migration guide:
[MIGRATION-9.0.md](../MIGRATION-9.0.md).

### Added
- `KafkaMessagePublisherAdapter(…, boolean legacyTraceparentHeader)`, fed by
  `codehunters.outbox.publisher.legacy-traceparent-header` (default `false`).

### Fixed
- Outbox records carried no trace context at all. The adapter now adds the `traceparent`
  record header from the context stored with the row.

### Behaviour changes
- Every outbox record with a stored context carries a `traceparent` header, unchanged;
  `x-traceparent` only when the switch is on.

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
