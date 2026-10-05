# Changelog — sdk-event-messaging-kafka

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
