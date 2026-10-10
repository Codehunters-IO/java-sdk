# sdk-event-messaging-core

Transactional outbox for Spring Boot 4: event envelope, factories, ports, the dispatch use case and
the shared configuration. It sends nothing on its own; add `sdk-event-messaging-jdbc` and one
transport.

```kotlin
implementation("io.codehunters:sdk-event-messaging-jdbc:9.0.0")
implementation("io.codehunters:sdk-event-messaging-rabbit:9.0.0") // or -kafka
```

Depend on `-core` alone only to build events without storing or sending them.

| Artifact | Provides |
|---|---|
| `sdk-event-messaging-core` | Model, factories, ports, `SendOutboxMessagesUseCase`, `codehunters.outbox.*` |
| [`sdk-event-messaging-jdbc`](../sdk-event-messaging-jdbc/README.md) | PostgreSQL outbox store, ShedLock schedulers, purge |
| [`sdk-event-messaging-rabbit`](../sdk-event-messaging-rabbit/README.md) | RabbitMQ publisher with confirms and mandatory returns |
| [`sdk-event-messaging-kafka`](../sdk-event-messaging-kafka/README.md) | Kafka publisher with a dedicated idempotent producer |

## Model

`EventMessage<T>` is the envelope sent to the broker:

| Part | Fields |
|---|---|
| `header` | `traceId`, `version` (`1.0.0`), `schemaVersion` (`1.0.0` unless set), `origin`, `timestamp` (`Instant`, an ISO-8601 string on the wire), `locale` |
| `body` | `aggregate`, `eventName`, `eventType`, `payload` |

`OutboxMessage<T>` is one outbox row: `topic` (exchange or topic), optional `routingKey`, `key`
(the aggregate), the serialized message, `status` (`PENDING` → `IN_FLIGHT` → `PUBLISHED`, or
`FAILED`), `attempts`, `error`, claim columns and `Instant` timestamps.

All of them are immutable records: read them with accessors (`message.id()`,
`event.header().traceId()`). `OutboxMessage` changes state through methods that return a new
message (`published(at)`, `attemptFailed(error)`, `retryAt(at)`, `failed(error)`, `released(at)`,
`sendStarted(at)`) or through `toBuilder()`.

## Writing an event

```java
OutboxEvent<OrderPlaced> event =
    OutboxEvent.to("orders")
        .key(order.id())
        .type("OrderPlaced")
        .payload(placed)
        .routingKey("order.placed"); // optional
outboxWriter.save(outboxEventFactory.create(event)); // inside the business transaction
```

`OutboxEvent` is built in stages, so the topic, the key (the aggregate and per-key ordering key, a
non-blank `String`), the event name and the payload are always given. The other components have
defaults and copy methods that return a new event:

| Method | Default |
|---|---|
| `routingKey(...)` | None; ignored by Kafka |
| `eventType(...)` | `EventTypes.EVENT` |
| `locale(...)` | None (`header.locale` is null) |
| `traceId(...)` | None: the current trace |
| `schemaVersion(...)` | `1.0.0`; bump it on a breaking payload change |

| Type | Use |
|---|---|
| `OutboxWriter` | `save(message)`: stores a row in the caller's transaction; the port applications inject |
| `OutboxEventFactory` | `create(event)`: the envelope and its outbox row, with a new id, the origin and the current `traceparent` |
| `EventMessageFactory` | `create(event)`: the envelope only; `origin` is `spring.application.name`, `timestamp` comes from the outbox clock |

A row without a key (unordered, never blocked) can be built by hand with `OutboxMessage.builder()`
and saved through `OutboxWriter`.

A `null` or blank trace id takes the current trace when `sdk-tracing` is on the classpath and its
`TraceContextPort` bean exists; an explicit trace id always wins. Without `sdk-tracing` the header
keeps what the caller passed.

## Trace continuity

A row is published later, by a scheduled dispatch that has its own trace. So that the consumer
continues the trace of the request that wrote the row, not the dispatcher's:

1. `OutboxEventFactory` stores the current W3C context on the row (`OutboxMessage.traceparent`,
   column `traceparent`), read from `TraceIdSource.currentTraceParent()`; with `sdk-tracing`, that
   is `TraceContextPort.currentTraceParent()`. Nothing is stored when no trace is active, or when
   an explicit trace id names another trace than the current one. Build the row on the thread that
   does the work; a row built by hand can set `traceparent` itself.
2. The transport adapter sends the stored value, unchanged, as the `traceparent` header (and as
   `x-traceparent` when `publisher.legacy-traceparent-header=true`). The consumer's span thus
   parents on the span that wrote the row. The outbox templates do not enable Boot's observation,
   so no dispatcher context can override it.
3. With Micrometer Tracing and a `Tracer` bean, `TracedMessagePublisher` makes the stored context
   current around each publish: logs carry the request's trace id, and any span or observation
   started inside the publish (for example on a template you instrument yourself) is a child of
   the stored span, not of the scheduled run. The dispatcher's context comes back afterwards.

Rows written before the `traceparent` column existed, or without a trace, are sent without the
header.

## Dispatch semantics

`SendOutboxMessagesUseCase` runs right after a transaction that saved outbox rows commits, and on
every poll (both from `-jdbc`). It reads the outbox through `OutboxDispatchStore` and is configured
with one `DispatchSettings(ClaimRequest, RetryPolicy, Clock)`. `execute()` returns `true` when the
claim came back full, so more rows may be waiting.

| Rule | Behaviour |
|---|---|
| Delivery | At-least-once. Consumers deduplicate on the `outbox-message-id` header |
| Claim | Up to `batch-size` due `PENDING` rows, plus `IN_FLIGHT` rows whose claim is older than `claim.ttl`, are marked `IN_FLIGHT` for this instance. Concurrent claimers receive disjoint rows |
| Stale reclaim | Counts as an attempt when its send had started: the previous dispatcher may already have sent it |
| Reclaim with `attempts ≥ max-attempts` | Marked `FAILED` with error `max-attempts-exceeded`, not sent again |
| Time budget | Sending stops once half of `claim.ttl` has passed since the claim; the rest of the batch goes back to `PENDING`, so no other instance reclaims rows still being sent |
| Publish failure | `attempts + 1`, retried after `backoff.base-delay x 2^(attempts - 1)`, capped at `backoff.max-delay`; `FAILED` once `attempts` reaches `max-attempts` |
| Ordering | Per `(origin, topic, message_key)`, in insertion order (`seq`). One claim takes up to `claim.max-per-key` consecutive rows of a key. A row is not claimed while an earlier row with the same key is `FAILED`, `PENDING` but not yet due, or `IN_FLIGHT` under another live claim |
| Key stops | When a row of a key is not published (failure, refused mark, time budget), the later rows of that key in the batch go back to `PENDING` unsent |
| Head-of-line blocking | A `FAILED` row blocks every later row with the same key until it is requeued or deleted. Rows without a key are not ordered and never blocked |

`FAILED` rows are never purged. Requeue them through `OutboxDispatchStore`:

```java
outboxDispatchStore.requeueFailed(orderId); // every FAILED row with this key, in any topic
outboxDispatchStore.requeue(ids);           // only the listed FAILED rows
```

Both put `FAILED` rows of this origin back to `PENDING`, due now, with `attempts = 0` and no error,
claim or send mark, return the number of rows moved, and request a dispatch once the surrounding
transaction commits. `requeue` binds the ids into one `IN (...)` list: chunk very large lists.

At startup, `claim.ttl` must exceed `batch-size` times the transport timeout
(`publisher.rabbit.confirm-timeout` or `publisher.kafka.send-timeout`); otherwise a batch still
being sent could be reclaimed and published twice, and startup fails. While scheduling is on, the
dispatch lock `task.lock-at-most` must also exceed one dispatch run, `claim.ttl / 2` plus the
transport timeout (155 s with the defaults); otherwise ShedLock frees the lock mid-run and startup
fails naming the keys. Raise the lock when you raise `claim.ttl`.

## Metrics

| Meter | Type | Tags | Source |
|---|---|---|---|
| `outbox.publish` | Timer, one sample per send | `outcome` (`success`/`failure`), `exception` (simple class name or `none`) | `-core`, when Micrometer and a `MeterRegistry` bean are present |
| `outbox_failed_messages` | Gauge, `FAILED` rows of this origin | `origin` | `-jdbc`; counts at most once per 30 s, scrapes in between reuse the last count |

Alert on `outbox_failed_messages > 0`: those rows block their key.

## Configuration

All keys are under `codehunters.outbox`. Invalid values fail startup with a message naming the key.
Durations accept `30s`, `5m` or ISO-8601 (`PT5M`); a plain number means milliseconds.

| Key | Default | Notes |
|---|---|---|
| `enabled` | `true` | `false` registers nothing in any outbox artifact |
| `batch-size` | `50` | ≥ 1 |
| `max-attempts` | `5` | ≥ 1 |
| `claim.ttl` | `5m` | > 0; must exceed the claim budget above (was `claim.ttl-ms`) |
| `claim.max-per-key` | `10` | ≥ 1; rows of one key claimed together. `1` claims a key's next row only once every earlier one is resolved |
| `backoff.base-delay` | `30s` | > 0; doubled per attempt (was `backoff.base-delay-ms`) |
| `backoff.max-delay` | `1h` | > 0 (was `backoff.max-delay-ms`) |
| `publisher.type` | unset | `rabbit` or `kafka`, case-insensitive; only needed with both transports present |
| `publisher.rabbit.confirm-timeout` | `5s` | > 0 |
| `publisher.kafka.send-timeout` | `5s` | > 0 (was `10s` in 5.x) |
| `publisher.legacy-traceparent-header` | `false` | `true` also sends the stored context as `x-traceparent`, for consumers that read only that header |
| `scheduling.enabled` | `true` | `false` leaves dispatch and purge scheduling to the application, see [`-jdbc`](../sdk-event-messaging-jdbc/README.md) |
| `scheduling.dispatch-on-commit` | `true` | Dispatch right after a transaction that saved outbox rows commits, see [`-jdbc`](../sdk-event-messaging-jdbc/README.md) |
| `task.*`, `purge.*` | | Scheduler settings, see [`-jdbc`](../sdk-event-messaging-jdbc/README.md) |

## Extension points

Every bean backs off when the application defines its own: `EventMessageFactory`,
`OutboxEventFactory`, `SendOutboxMessagesPort`, `TraceIdSource`. The outbox keeps its own clock,
the bean `outboxClock` (`Clock.systemUTC()`, not a default candidate, injected by qualifier); an
application `Clock` bean is neither replaced nor used. Define a bean named `outboxClock` to move the
outbox's time, for example in tests.
`MessagePublisherDecorator` beans wrap the publisher handed to the use case, in bean order; the
`outbox.publish` timer is one, and so is the stored-trace restoration (`outboxTraceContextDecorator`,
registered when a Micrometer `Tracer` bean exists).

## Known limits

- **A reclaim counts only rows whose send had started.** The dispatcher marks each row right before
  publishing it; when it crashes or stalls past `claim.ttl`, the next claim adds one attempt to
  marked rows only. A row that crashes the dispatcher while being sent is charged on every reclaim,
  so a poison message still ends up `FAILED` (fixed in 7.0; 6.x charged every row of the batch).
- **Maven builds get no artifact-alignment protection.** The Gradle capability that makes a build
  fail when the 5.x `sdk-event-messaging` jar is still resolved, or when artifacts of different
  versions meet, is invisible to Maven. Import `io.codehunters:sdk-bom` so all
  `sdk-event-messaging-*` artifacts resolve to the same version, and remove the 5.x dependency.

## Packages

| Package | Contents |
|---|---|
| `io.codehunters.messaging` | Model (`EventMessage`, `OutboxMessage`, `OutboxEvent`), ports (`OutboxWriter`, `OutboxDispatchStore`, `MessagePublisherPort`, `SendOutboxMessagesPort`, `OutboxDispatchTrigger`), `ClaimRequest`, `MessagePublishException`, `MessagePublisherDecorator` |
| `io.codehunters.messaging.factory` | `EventMessageFactory`, `OutboxEventFactory`, `TraceIdSource` |
| `io.codehunters.messaging.dispatch` | `SendOutboxMessagesUseCase`, `DispatchSettings`, `RetryPolicy`, `CoalescingDispatchTrigger` |
| `io.codehunters.messaging.metrics` | `MeteredMessagePublisher` |
| `io.codehunters.messaging.tracing` | `TracedMessagePublisher` |
| `io.codehunters.messaging.config` | Auto-configuration, `EventMessagingProperties`, transport condition |

## Migrating from 8.x

The outbox supports PostgreSQL only, `claim.dialect` is gone, the old `outbox.*` keys are ignored
and rows store their `traceparent` (a new column to add before deploying). Events are written with
`OutboxEvent` and `OutboxWriter` (the locale no longer defaults to `es`), the model is made of
records, `OutboxRepositoryPort` is split into `OutboxWriter` and `OutboxDispatchStore`, the timing
keys are durations (`claim.ttl-ms` → `claim.ttl`), and the outbox uses its own `outboxClock`; see
[MIGRATION-9.0.md](../MIGRATION-9.0.md).

## Migrating from 7.x

Only imports change; see [MIGRATION-8.0.md](../MIGRATION-8.0.md), then
[MIGRATION-9.0.md](../MIGRATION-9.0.md).

## Migrating from 5.x

See [MIGRATION-6.0.md](../MIGRATION-6.0.md): coordinates, property keys, transport selection,
RabbitMQ settings, DDL and consumer deduplication.
