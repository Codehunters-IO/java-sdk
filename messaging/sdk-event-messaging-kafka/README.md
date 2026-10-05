# sdk-event-messaging-kafka

Kafka publisher for the transactional outbox, through a dedicated idempotent producer. Model and
dispatch semantics are described in
[`sdk-event-messaging-core`](../sdk-event-messaging-core/README.md).

```kotlin
implementation("io.codehunters:sdk-event-messaging-jdbc:6.0.0")
implementation("io.codehunters:sdk-event-messaging-kafka:6.0.0")
implementation("org.springframework.boot:spring-boot-starter-kafka")
```

`spring-kafka` is not brought transitively. The publisher is active when `spring-kafka` is on the
classpath, a `ProducerFactory` bean exists (Boot's Kafka auto-configuration provides one) and Kafka
is the outbox transport (this artifact alone, or `codehunters.outbox.publisher.type=kafka`).

## How it publishes

| Aspect | Behaviour |
|---|---|
| Producer | Its own `outboxKafkaProducerFactory` and `outboxKafkaTemplate`, derived from the application's `ProducerFactory` configuration (bootstrap servers, security, tuning). Neither is a default candidate |
| Forced settings | `StringSerializer` for key and value, `acks=all`, `enable.idempotence=true`; any `transactional.id` is dropped |
| Destination | Topic = outbox `topic`; the outbox `routingKey` is ignored |
| Key | Outbox `key` (the event aggregate), so one aggregate's events share a partition and keep order |
| Value | The stored JSON payload, sent as is; an application JSON serializer can no longer double-encode it |
| Headers | `outbox-message-id` = outbox row id, plus a JSON content type |
| Send | Blocks for the broker acknowledgement, at most `send-timeout` |
| Trace | No `traceparent` header; the trace id travels in the envelope's `header.traceId` |

Outbox sends run outside Kafka transactions. Exactly-once comes from the consumer, not the producer:
the idempotent producer only removes duplicates caused by its own retries within one session.

## Failure semantics

| Situation | Result |
|---|---|
| Broker error, or no acknowledgement within `send-timeout` | `MessagePublishException`; retried with backoff, `FAILED` after `max-attempts` |
| Acknowledgement lost after the broker wrote the record | Sent again on retry: delivery is at-least-once |

Consumers must deduplicate on the `outbox-message-id` header.

## Configuration

Keys under `codehunters.outbox`; the rest are in the [core README](../sdk-event-messaging-core/README.md).

| Key | Default | Notes |
|---|---|---|
| `publisher.type` | unset | Set to `kafka` only when `-rabbit` is also on the classpath |
| `publisher.kafka.send-timeout` | `5s` | > 0. Startup fails unless `claim.ttl-ms > batch-size x send-timeout` (default 50 x 5 s < 300 s) |

Producer settings come from `spring.kafka.producer.*` / `spring.kafka.*`, except the forced ones
above.

## Extension points

Define beans named `outboxKafkaProducerFactory` or `outboxKafkaTemplate` to replace the producer,
or a `MessagePublisherPort` to replace the publisher.

## Migrating from 5.x

| 5.x | 6.0 |
|---|---|
| `sdk-event-messaging:5.x` with `outbox.publisher.type=kafka` | `sdk-event-messaging-jdbc` + `sdk-event-messaging-kafka` 6.0.0; the type is optional |
| Required a `KafkaTemplate` bean and sent through it | Requires a `ProducerFactory` bean; sends through `outboxKafkaTemplate` |
| Application value serializer applied to the payload | `StringSerializer`, payload sent as stored |
| `outbox.publisher.kafka.send-timeout` default `10s` | `codehunters.outbox.publisher.kafka.send-timeout` default `5s` |

If you raised `batch-size` or `send-timeout`, check that `claim.ttl-ms` still exceeds their product.
Full guide: [MIGRATION-6.0.md](../MIGRATION-6.0.md).
