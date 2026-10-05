# sdk-event-messaging-core

Transactional outbox for Spring Boot 4: event envelope, factories, ports, the dispatch use case and
the shared configuration. It sends nothing on its own; add `sdk-event-messaging-jdbc` and one
transport.

```kotlin
implementation("io.codehunters:sdk-event-messaging-jdbc:6.0.0")
implementation("io.codehunters:sdk-event-messaging-rabbit:6.0.0") // or -kafka
```

Depend on `-core` alone only to build events without storing or sending them.

| Artifact | Provides |
|---|---|
| `sdk-event-messaging-core` | Model, factories, ports, `SendOutboxMessagesUseCase`, `codehunters.outbox.*` |
| [`sdk-event-messaging-jdbc`](../sdk-event-messaging-jdbc/README.md) | Outbox table store, claim dialects, ShedLock schedulers, purge |
| [`sdk-event-messaging-rabbit`](../sdk-event-messaging-rabbit/README.md) | RabbitMQ publisher with confirms and mandatory returns |
| [`sdk-event-messaging-kafka`](../sdk-event-messaging-kafka/README.md) | Kafka publisher with a dedicated idempotent producer |

## Model

`EventMessage<T>` is the envelope sent to the broker:

| Part | Fields |
|---|---|
| `header` | `traceId`, `version` (`1.0.0`), `schemaVersion` (`1.0.0`), `origin`, `timestamp`, `locale` |
| `body` | `aggregate`, `eventName`, `eventType`, payload |

`OutboxMessage<T>` is one outbox row: `topic` (exchange or topic), optional `routingKey`, `key`
(the aggregate), the serialized message, `status` (`PENDING` → `IN_FLIGHT` → `PUBLISHED`, or
`FAILED`), `attempts`, `error` and claim columns.

## Writing an event

```java
OutboxMessage<EventMessage<OrderPlaced>> row =
    outboxEventFactory.createRoutingKey(
        "orders", "order.placed", null, order.id(), "OrderPlaced", EventTypes.EVENT, payload);
outboxRepositoryPort.save(row); // inside the same transaction as the business write
```

| Factory | Use |
|---|---|
| `EventMessageFactory` | Builds the envelope; `origin` is `spring.application.name` |
| `OutboxEventFactory` | Builds the envelope and its outbox row; `create(...)` without routing key, `createRoutingKey(...)` with one; locale `es` unless given |

A `null` or blank trace id takes the current trace when `sdk-tracing` is on the classpath and its
`TraceContextPort` bean exists; an explicit trace id always wins. Without `sdk-tracing` the header
keeps what the caller passed.

## Dispatch semantics

`SendOutboxMessagesUseCase` runs on every scheduler tick (from `-jdbc`), on one instance at a time.

| Rule | Behaviour |
|---|---|
| Delivery | At-least-once. Consumers deduplicate on the `outbox-message-id` header |
| Claim | Up to `batch-size` due `PENDING` rows, plus `IN_FLIGHT` rows whose claim is older than `claim.ttl-ms`, are marked `IN_FLIGHT` for this instance |
| Stale reclaim | Counts as an attempt: the previous dispatcher may already have sent it |
| Reclaim with `attempts ≥ max-attempts` | Marked `FAILED` with error `max-attempts-exceeded`, not sent again |
| Time budget | Sending stops once half of `claim.ttl-ms` has passed since the claim; the rest of the batch goes back to `PENDING`, so no other instance reclaims rows still being sent |
| Publish failure | `attempts + 1`, retried after `backoff.base-delay-ms x 2^(attempts - 1)`, capped at `backoff.max-delay-ms`; `FAILED` once `attempts` reaches `max-attempts` |
| Ordering | Per `(origin, topic, message_key)`, in insertion order (`seq`). A row is not claimed while an earlier row with the same key is `PENDING`, `IN_FLIGHT` or `FAILED` |
| Head-of-line blocking | A `FAILED` row blocks every later row with the same key until it is requeued or deleted. Rows without a key are not ordered and never blocked |

`FAILED` rows are never purged. To requeue one, set `status = 'PENDING'`, `attempts = 0` and
`next_attempt_at = NULL`; with `attempts` left at `max-attempts` the next claim marks it `FAILED`
again.

At startup, `claim.ttl-ms` must exceed `batch-size` times the transport timeout
(`publisher.rabbit.confirm-timeout` or `publisher.kafka.send-timeout`); otherwise a batch still
being sent could be reclaimed and published twice, and startup fails. While scheduling is on, the
dispatch lock `task.lock-at-most` must also exceed one dispatch run, `claim.ttl-ms / 2` plus the
transport timeout (155 s with the defaults); otherwise ShedLock frees the lock mid-run and startup
fails naming the keys. Raise the lock when you raise `claim.ttl-ms`.

## Metrics

| Meter | Type | Tags | Source |
|---|---|---|---|
| `outbox.publish` | Timer, one sample per send | `outcome` (`success`/`failure`), `exception` (simple class name or `none`) | `-core`, when Micrometer and a `MeterRegistry` bean are present |
| `outbox_failed_messages` | Gauge, `FAILED` rows of this origin | `origin` | `-jdbc`; queries the table on each scrape |

Alert on `outbox_failed_messages > 0`: those rows block their key.

## Configuration

All keys are under `codehunters.outbox`. Invalid values fail startup with a message naming the key.

| Key | Default | Notes |
|---|---|---|
| `enabled` | `true` | `false` registers nothing in any outbox artifact |
| `batch-size` | `50` | ≥ 1 |
| `max-attempts` | `5` | ≥ 1 |
| `claim.ttl-ms` | `300000` | ≥ 1; must exceed the claim budget above |
| `claim.dialect` | `auto` | See [`-jdbc`](../sdk-event-messaging-jdbc/README.md) |
| `backoff.base-delay-ms` | `30000` | ≥ 1; doubled per attempt |
| `backoff.max-delay-ms` | `3600000` | ≥ 1 |
| `publisher.type` | unset | `rabbit` or `kafka`, case-insensitive; only needed with both transports present |
| `publisher.rabbit.confirm-timeout` | `5s` | > 0 |
| `publisher.kafka.send-timeout` | `5s` | > 0 (was `10s` in 5.x) |
| `scheduling.enabled` | `true` | `false` leaves dispatch and purge scheduling to the application, see [`-jdbc`](../sdk-event-messaging-jdbc/README.md) |
| `task.*`, `purge.*` | | Scheduler settings, see [`-jdbc`](../sdk-event-messaging-jdbc/README.md) |

## Extension points

Every bean backs off when the application defines its own: `Clock` (UTC by default),
`EventMessageFactory`, `OutboxEventFactory`, `SendOutboxMessagesPort`, `TraceIdSource`.
`MessagePublisherDecorator` beans wrap the publisher handed to the use case, in bean order; the
`outbox.publish` timer is one.

## Migrating from 5.x

See [MIGRATION-6.0.md](../MIGRATION-6.0.md): coordinates, property keys, transport selection,
RabbitMQ settings, DDL and consumer deduplication.
