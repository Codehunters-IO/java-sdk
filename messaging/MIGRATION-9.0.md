# Migrating sdk-event-messaging to 9.0

9.0 makes the outbox PostgreSQL-only, drops the pre-6.0 property names and stores the request's
trace context with each row so the published message continues it. It also reworks the developer
surface: events are written through a fluent `OutboxEvent` and the `OutboxWriter` port, the model
is made of immutable records with `Instant` timestamps, `OutboxRepositoryPort` is split in two, the
timing properties are durations, the outbox schedules itself without turning on application-wide
scheduling, and messages are dispatched as soon as their transaction commits. The four artifacts
(`-core`, `-jdbc`, `-rabbit`, `-kafka`) move to 9.0.0 together; import the matching
`io.codehunters:sdk-bom` to keep them aligned. The Java packages do not change; the PostgreSQL DDL
gains one nullable column.

**Coming from 7.x?** Apply [MIGRATION-8.0.md](MIGRATION-8.0.md) first (imports only). From 6.x,
start with [MIGRATION-7.0.md](MIGRATION-7.0.md) (the `send_started_at` column); from 5.x, with
[MIGRATION-6.0.md](MIGRATION-6.0.md).

## Checklist

1. Make sure the outbox runs on PostgreSQL. Other databases are no longer supported.
2. **Before deploying 9.0**, add the `traceparent` column to `outbox_message` (see
   [traceparent column](#traceparent-column)).
3. Rename any remaining `outbox.*` key to `codehunters.outbox.*`; the old names are ignored now.
4. Remove `codehunters.outbox.claim.dialect` from your configuration.
5. Rename `claim.ttl-ms`, `backoff.base-delay-ms` and `backoff.max-delay-ms` (see
   [Renamed and duration-typed properties](#renamed-and-duration-typed-properties)).
6. Replace `createRoutingKey(...)` / `create(...)` and `OutboxRepositoryPort.save` with
   `OutboxEvent` and `OutboxWriter` (see [Writing events](#writing-events)). Set `.locale("es")`
   where consumers relied on the old default locale.
7. Replace `getX()` calls on `EventMessage` and `OutboxMessage` with record accessors, and
   `LocalDateTime` with `Instant` (see [Immutable records](#immutable-records)).
8. Move code that used `OutboxRepositoryPort` or the `outboxRepositoryPort` bean to `OutboxWriter`
   / `OutboxDispatchStore` (see [Port split](#port-split)).
9. Recompile if you build `SendOutboxMessagesUseCase`, `OutboxJdbcAdapter`,
   `OutboxAdapterSettings`, the factories or the schedulers yourself, or reference a dialect class
   (see [Removed API](#removed-api)).
10. If your own `@Scheduled` or `@SchedulerLock` methods ran only because the outbox turned
    scheduling on, add `@EnableScheduling` (and `@EnableSchedulerLock`) to your application (see
    [Scheduling](#scheduling)).
11. If you relied on your application `Clock` bean driving the outbox (for example in tests), define
    a bean named `outboxClock` instead (see [Clock](#clock)).
12. Review `task.lock-at-least` (now `0`), `claim.max-per-key` (`10`) and
    `scheduling.dispatch-on-commit` (`true`) (see [Throughput](#throughput)).
13. Replace hand-written requeue SQL with `requeue` / `requeueFailed` (see
    [Requeuing FAILED rows](#requeuing-failed-rows)).
14. Check the outbox JSON if your application customises Jackson globally (see
    [Outbox JSON mapper](#outbox-json-mapper)).
15. Fix tests that run the outbox on an embedded H2 (see [Tests on H2](#tests-on-h2)).
16. If a consumer reads only `x-traceparent`, set
    `codehunters.outbox.publisher.legacy-traceparent-header=true` (see
    [Trace continuity](#trace-continuity)).
17. Make sure the application has a Micrometer tracer bridge, or events carry no trace (see
    [Without a tracer bridge](#without-a-tracer-bridge)).
18. Bump the dependencies (or the BOM) to 9.0.0.

## PostgreSQL only

The adapter issues the PostgreSQL claim (`FOR UPDATE SKIP LOCKED` with `RETURNING`) and purge
directly; there is no dialect to choose any more. Other databases are no longer supported: at
startup the SDK reads the database product name and fails with "The outbox supports PostgreSQL
only, but the data source is <product>". A database that cannot be reached at startup is not
checked; the first claim then fails if it is not PostgreSQL.

The check reads the JDBC driver's product name, not the server's: pgjdbc reports `PostgreSQL` for
every server it talks to. Amazon Redshift, CockroachDB or YugabyteDB reached through pgjdbc pass
the check but may fail at the first claim, which needs `FOR UPDATE ... SKIP LOCKED`. A vendor
driver that reports another name, such as EDB's (`EnterpriseDB`), is rejected.

Only the PostgreSQL reference scripts remain in the `-jdbc` jar:

| File | Use |
|---|---|
| `db/migration/outbox/postgresql/outbox_message_create.sql` | New installs: `outbox_message` with `seq`, `traceparent`, indexes, `shedlock` |
| `db/migration/outbox/postgresql/outbox_message_add_send_started_at.sql` | Tables created by 6.x |
| `db/migration/outbox/postgresql/outbox_message_add_traceparent.sql` | Tables created by 6.x (after the line above), 7.x or 8.x |

## traceparent column

9.0 writes and claims `outbox_message.traceparent`, so saving and dispatch fail until the column
exists. Copy `postgresql/outbox_message_add_traceparent.sql` from the `-jdbc` jar into your
changelog (adapt the changeset author and id) and apply it **before** deploying 9.0:

```sql
--changeset codehunters-sdk:outbox-message-add-traceparent
ALTER TABLE outbox_message ADD COLUMN IF NOT EXISTS traceparent VARCHAR(55);
--rollback ALTER TABLE outbox_message DROP COLUMN traceparent;
```

The column is nullable without a default, so adding it does not rewrite the table, and 8.x ignores
it: add it while 8.x still runs, then roll out 9.0. Running it twice is harmless. Rows written
before the upgrade keep `NULL` and are published without a `traceparent` header.

## Writing events

The positional factory methods are gone. An event is described by an immutable `OutboxEvent`,
built in stages that make the topic, key, event name and payload mandatory, then turned into an
outbox row by `OutboxEventFactory.create(event)` and stored with `OutboxWriter.save(...)`, inside
the transaction of the business write.

Before (8.x):

```java
OutboxMessage<EventMessage<OrderPlaced>> row =
    outboxEventFactory.createRoutingKey(
        "orders", "order.placed", null, order.id(), "OrderPlaced", EventTypes.EVENT, placed);
outboxRepositoryPort.save(row);
```

After (9.0):

```java
OutboxEvent<OrderPlaced> event =
    OutboxEvent.to("orders")
        .key(order.id())
        .type("OrderPlaced")
        .payload(placed)
        .routingKey("order.placed");
outboxWriter.save(outboxEventFactory.create(event));
```

| 8.x argument | 9.0 |
|---|---|
| `topic` | `OutboxEvent.to(topic)` |
| `aggregate` | `.key(...)`: the envelope's `aggregate` and the per-key ordering key; a non-blank `String` |
| `eventName` | `.type(...)` |
| `payload` | `.payload(...)`; not null |
| `routingKey` (`createRoutingKey` only) | `.routingKey(...)`; none by default |
| `eventType` | `.eventType(...)`; `EventTypes.EVENT` by default |
| `traceId` | `.traceId(...)`; null or blank (the default) takes the current trace |
| `locale` | `.locale(...)`; **null by default** |
| — | `.schemaVersion(...)`; `1.0.0` by default, bump it on a breaking payload change |

The copy methods (`routingKey`, `eventType`, `locale`, `traceId`, `schemaVersion`) each return a
new event. `EventMessageFactory.create(event)` builds only the envelope, for callers that do not
store it.

**The locale no longer defaults to `"es"`.** `create(...)` and the shorter `createRoutingKey(...)`
stamped `"es"` on every envelope; 9.0 leaves `header.locale` null unless you call `.locale(...)`.
Add `.locale("es")` where consumers read it.

The key is now mandatory. A row without a key (unordered, never blocked) can still be built by hand
with `OutboxMessage.builder()` and saved through `OutboxWriter`.

## Immutable records

`EventMessage`, `EventMessage.Header`, `EventMessage.Body` and `OutboxMessage` are records. The
Lombok getters, setters and builders of the envelope are gone; `OutboxMessage` keeps a hand-written
`builder()` / `toBuilder()`.

| 8.x | 9.0 |
|---|---|
| `event.getHeader().getTraceId()`, `event.getBody().getPayload()` | `event.header().traceId()`, `event.body().payload()` |
| `message.getId()`, `getTopic()`, `getKey()`, `getMessage()`, `getStatus()`, `getTraceparent()` | `message.id()`, `topic()`, `key()`, `message()`, `status()`, `traceparent()` |
| `EventMessage.Header#getTimestamp()`: `String` | `header().timestamp()`: `Instant` |
| `OutboxMessage` times (`occurredAt`, `nextAttemptAt`, `publishedAt`, `claimedAt`, `sendStartedAt`): `LocalDateTime` in UTC | `Instant` |
| `message.setStatus(...)`, `setAttempts(...)`, … | Transitions that return a new message: `sendStarted(at)`, `published(at)`, `attemptFailed(error)`, `retryAt(at)`, `failed(error)`, `released(at)`, or `toBuilder()` |
| `EventMessage.builder()`, `Header.builder()`, `Body.builder()` | The canonical constructors, or `EventMessageFactory.create(event)` |

The envelope's wire format does not change: the same field names, and `header.timestamp` is still
an ISO-8601 string. The column types do not change either; the JDBC store converts `Instant` to UTC
`timestamp` values and back.

## Port split

`OutboxRepositoryPort` mixed what applications need (saving) with what the dispatcher, the purge
job and operators need. It is replaced by two ports in `io.codehunters.messaging`:

| 8.x `OutboxRepositoryPort` | 9.0 |
|---|---|
| `save(message)` | `OutboxWriter.save(message)`: the port applications inject |
| `claimPendingMessages(origin, batchSize, claimedBy, claimTtl)` | `OutboxDispatchStore.claim(new ClaimRequest(origin, claimedBy, batchSize, claimTtl, maxPerKey))` |
| `boolean markSendStarted(message)` | `Optional<OutboxMessage<String>> markSendStarted(message)`: the marked message, empty when the claim was taken over |
| `update(message)` | `OutboxDispatchStore.update(message)` |
| `purgePublished(LocalDateTime cutoff, int batchSize)` | `OutboxDispatchStore.purgePublished(Instant cutoff, int batchSize)` |
| `countFailed()` | `OutboxDispatchStore.countFailed()` |
| — | `OutboxDispatchStore.requeue(ids)`, `requeueFailed(key)` (see [Requeuing FAILED rows](#requeuing-failed-rows)) |

`OutboxJdbcAdapter` implements both. The auto-configured bean is renamed from
`outboxRepositoryPort` (typed `OutboxRepositoryPort`) to `outboxJdbcAdapter` (typed
`OutboxJdbcAdapter`); update any `@Qualifier("outboxRepositoryPort")`. It backs off when the
application defines an `OutboxWriter` or an `OutboxDispatchStore` bean.

## Dispatch use case

`SendOutboxMessagesUseCase` takes the store, the publisher and one `DispatchSettings` instead of
nine or ten positional arguments:

```java
// 8.x
new SendOutboxMessagesUseCase(
    repository, publisher, 50, 5, instanceId, origin, 300_000, 30_000, 3_600_000, clock);

// 9.0
ClaimRequest claim = new ClaimRequest(origin, instanceId, 50, Duration.ofMinutes(5), 10);
RetryPolicy retry = new RetryPolicy(5, Duration.ofSeconds(30), Duration.ofHours(1));
SendOutboxMessagesPort dispatch =
    new SendOutboxMessagesUseCase(store, publisher, new DispatchSettings(claim, retry, clock));
```

`ClaimRequest`, `RetryPolicy` and `DispatchSettings` are records (`io.codehunters.messaging` and
`io.codehunters.messaging.dispatch`) that validate their values.

`SendOutboxMessagesPort.execute()` returns `boolean`: `true` when the claim came back full, so more
rows may be waiting. Callers that ignore it compile unchanged; an application that triggers
dispatch itself can loop while it returns `true` to drain a backlog. Implementations of the port
must return a value.

## Renamed and duration-typed properties

| 8.x key (default) | 9.0 key (default) |
|---|---|
| `codehunters.outbox.claim.ttl-ms` (`300000`) | `codehunters.outbox.claim.ttl` (`5m`) |
| `codehunters.outbox.backoff.base-delay-ms` (`30000`) | `codehunters.outbox.backoff.base-delay` (`30s`) |
| `codehunters.outbox.backoff.max-delay-ms` (`3600000`) | `codehunters.outbox.backoff.max-delay` (`1h`) |

There are no aliases: an old key still set is ignored at runtime and the default applies. The
configuration metadata lists the three old keys as deprecated at error level, with the new key as
the replacement, so IDEs and `spring-boot-properties-migrator` flag them. Environment variables
follow (`CODEHUNTERS_OUTBOX_CLAIM_TTL_MS` → `CODEHUNTERS_OUTBOX_CLAIM_TTL`).

Every timing key is now a `java.time.Duration`: `claim.ttl`, `backoff.base-delay`,
`backoff.max-delay`, `task.fixed-delay` (`5s`), `task.lock-at-most` (`5m`), `task.lock-at-least`
(`0s`), `purge.fixed-delay` (`1h`), `purge.initial-delay` (`1m`), `purge.lock-at-most` (`10m`) and
`purge.lock-at-least` (`30s`). They accept `30s`, `5m` or ISO-8601 (`PT5M`); **a plain number still
means milliseconds**, so `task.fixed-delay=5000` keeps working. Values are checked at startup: the
delays and lock-at-most must be positive, `purge.initial-delay` and the lock-at-least values must
not be negative, and a lock-at-least must not exceed its lock-at-most.

The `EventMessagingProperties` accessors follow: `Claim(ttl, maxPerKey)`,
`Backoff(baseDelay, maxDelay)`, and `Duration` components in `Task` and `Purge`.

### New keys

| Key | Default | Use |
|---|---|---|
| `codehunters.outbox.claim.max-per-key` | `10` | Rows of one `(topic, key)` claimed together, in order; ≥ 1. `1` is the 8.x behaviour |
| `codehunters.outbox.scheduling.dispatch-on-commit` | `true` | Dispatch right after a transaction that saved outbox rows commits |

`codehunters.outbox.task.lock-at-least` now defaults to `0` (was `10000` ms); see
[Throughput](#throughput).

## Scheduling

8.x put `@EnableScheduling` and `@EnableSchedulerLock` on the application context and ran its
schedulers through `@Scheduled` / `@SchedulerLock`. 9.0 adds neither:

- The dispatch poll and the purge run on a private `outboxTaskScheduler` (a
  `ThreadPoolTaskScheduler` with 3 threads named `outbox-*`, not a default candidate, so lookups of
  `TaskScheduler` by type do not see it). The application's own task scheduler is never used.
- `outboxSchedulingLifecycle` (a `SmartLifecycle`) schedules every `OutboxScheduledTask` bean
  (`outboxDispatchTask`, `outboxPurgeTask`) and wraps each run in ShedLock programmatically, through
  the `LockProvider` bean.
- `OutboxScheduler` and `OutboxPurgeScheduler` are plain `Runnable`s: `processOutboxMessages()` and
  `purgePublished()` become `run()`. They are registered only while scheduling is on and a
  `LockProvider` bean exists.
- The lock names are unchanged: `task.lock-name` and `purge.lock-name` have no default, and the tasks
  use `outbox-<spring.application.name>` and `outbox-purge-<spring.application.name>` when they are
  unset. In 8.x the property defaults (`outbox-${spring.application.name:unknown}`) were never
  resolved, so code reading `EventMessagingProperties.Task#lockName()` got the literal placeholder;
  it now gets `null` unless the key is set.

**If your application has its own `@Scheduled` methods** and never enabled scheduling itself, they
ran only because the outbox did. Add `@EnableScheduling` to one of your configuration classes. The
same holds for your own `@SchedulerLock` methods: add `@EnableSchedulerLock(defaultLockAtMostFor =
"...")` (8.x set `60s`).

To replace the scheduler, define a bean named `outboxTaskScheduler`. It must be a `TaskScheduler`
and, while `scheduling.dispatch-on-commit` is on, an `Executor` too (`ThreadPoolTaskScheduler` is
both). `codehunters.outbox.scheduling.enabled=false` still leaves dispatch and purge to the
application; it now also turns off dispatch on commit.

## Clock

The outbox uses its own clock, the bean `outboxClock` (`Clock.systemUTC()`, not a default candidate),
injected by qualifier. In 8.x an application `Clock` bean replaced the outbox clock; it is now
neither replaced nor used. To move the outbox's time, for example in tests, define a bean named
`outboxClock`:

```java
@Bean(name = "outboxClock", defaultCandidate = false)
Clock outboxClock() {
  return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
}
```

## Throughput

In 8.x a message waited for the next poll, and one poll claimed one batch, at most one row per key.
9.0 changes three things:

- **Dispatch on commit.** After a transaction that saved outbox rows commits (or right after a save
  outside a transaction), the store asks `outboxDispatchTrigger` for a dispatch on the outbox
  scheduler. Requests coalesce: at most one run is pending while one is running, and a run whose
  claim came back full runs again. The poll stays as a safety net and picks up anything a request
  missed. Commit-triggered runs do not take the ShedLock dispatch lock; concurrent runs on several
  instances are safe because the claim skips rows locked by another claimer and fences every update.
- **Several rows per key.** A claim takes up to `claim.max-per-key` consecutive rows of one
  `(topic, key)`, in `seq` order. When one of them is not published, the later rows of that key in
  the batch go back to `PENDING` unsent, so a key is never delivered out of order.
- **`task.lock-at-least` defaults to `0`.** The 8.x value (`10000` ms) kept the poll lock for 10 s
  after a 5 s poll, so a run started only every 10 to 15 s. Set it back if you need a minimum gap.

Measured on PostgreSQL 17 (Testcontainers), a 4-connection pool and an in-memory publisher:

| | 8.x | 9.0 |
|---|---|---|
| Commit to publish | Up to one poll interval (`task.fixed-delay`, 5 s) | About 2.5 ms on average |
| Polling rate | At most 50 rows per 5 s poll, about 10 msg/s; about 3 to 5 msg/s with the 10 s lock-at-least | — |
| 2000-row backlog | — | About 1100 msg/s with `max-per-key=1`, about 1490 msg/s with `max-per-key=10` |
| Per key | One row per poll | Up to `max-per-key` rows per run |

With a real broker each publish waits for its acknowledgement and a run sends sequentially, so one
instance publishes about one message per acknowledgement latency (a 2 ms ack gives about 500
msg/s). An idle outbox still polls every `task.fixed-delay`.

## Requeuing FAILED rows

A `FAILED` row blocks every later row with its key. 8.x documented a manual `UPDATE`; 9.0 adds two
operations to `OutboxDispatchStore`:

```java
@Transactional
public int retryOrderEvents(String orderId) {
  return outboxDispatchStore.requeueFailed(orderId); // every FAILED row with this key, any topic
}

public int retry(List<UUID> ids) {
  return outboxDispatchStore.requeue(ids); // only the listed FAILED rows
}
```

Both touch only `FAILED` rows of the application's origin and put them back to `PENDING`, due now,
with `attempts = 0` and no error, claim or send mark. They return the number of rows requeued; an
empty id list returns `0` without a query. When at least one row moved, a dispatch is requested
after the surrounding transaction commits, or immediately without one. `requeue` binds the ids into
one `IN (...)` list, so split very large lists into chunks.

## Outbox JSON mapper

`outboxJsonMapper` is now built from the application's `JsonMapper.Builder` when Boot's Jackson
auto-configuration provides one, so `spring.jackson.*` settings, `JsonMapperBuilderCustomizer`
beans and registered modules apply to outbox payloads. Two settings are forced: indented output is
off, and dates are written as ISO-8601 strings, not timestamps. Without Boot's Jackson
auto-configuration the mapper is a plain `JsonMapper` with those two settings. The application's
own `JsonMapper` and builder are not changed.

A global customisation now reaches the whole stored message, envelope included: for example a
`SNAKE_CASE` naming strategy also renames `traceId` to `trace_id`. If consumers expect the 8.x
format, define your own bean named `outboxJsonMapper`.

## Trace continuity

Up to 8.x only the trace id reached the event envelope; the dispatch, a scheduled run with its own
trace, published under that run's context, so RabbitMQ consumers joined the scheduler's trace and
Kafka records carried no `traceparent` at all. In 9.0:

- `OutboxEventFactory` stores the current W3C context with the row (`OutboxMessage.traceparent`),
  from `sdk-tracing`'s `TraceContextPort` when it is present. A row you build by hand can set
  `traceparent` itself.
- The RabbitMQ and Kafka publishers send it unchanged as the `traceparent` header, so the consumer's
  span is a child of the span that wrote the row. They add `x-traceparent` only when
  `codehunters.outbox.publisher.legacy-traceparent-header=true` (default `false`).
- Every step checks the value with `W3cTraceParent` (exactly 55 lowercase-hex characters, version
  not `ff`, trace and span ids not all zeros). An invalid context is not stored by the factory, is
  saved as `NULL` by the JDBC store (a hand-built row never fails the business transaction), is not
  sent as a header and is not continued by `TracedMessagePublisher`. An explicit trace id
  (`OutboxEvent.traceId(...)`) stores the context only when it equals the context's trace id.
- With Micrometer Tracing and a `Tracer` bean, each publish runs with the stored context as the
  current one (`TracedMessagePublisher`): log lines written while publishing carry the request's
  trace id, and spans started there parent on it.

The SDK-owned `outboxRabbitTemplate` and `outboxKafkaTemplate` keep Boot's observation off; the
header comes from the adapter, not from the dispatching thread.

### Without a tracer bridge

The trace comes from `sdk-tracing`'s `TraceContextPort`. With a Micrometer tracer bridge
(`micrometer-tracing-bridge-otel` or `-brave`) it reads the current span. Without one it falls
back to the logging MDC, which `sdk-tracing` 3.0 no longer writes: unless your own code puts the
trace there, events created without an explicit trace id get an empty envelope `traceId`, and rows
store no `traceparent` and are published without the header. Add a tracer bridge to keep both.

## Tests on H2

An embedded H2 now fails startup with "The outbox supports PostgreSQL only, but the data source is
H2", even in `MODE=PostgreSQL` (the driver still reports H2). In `@SpringBootTest` or slice tests
that load the outbox auto-configuration, pick one:

- set `codehunters.outbox.enabled=false` in the test profile when the test does not exercise the
  outbox;
- run the test against PostgreSQL with Testcontainers;
- declare your own test bean, for example an in-memory fake, that implements **both**
  `OutboxWriter` and `OutboxDispatchStore`, so what the code under test saves is what the
  dispatcher claims: the SDK backs off its JDBC adapter, and the check, when either port exists.

```java
class InMemoryOutbox implements OutboxWriter, OutboxDispatchStore {
  @Override public <T> void save(OutboxMessage<T> message) { ... }
  @Override public List<OutboxMessage<String>> claim(ClaimRequest request) { ... }
  @Override public Optional<OutboxMessage<String>> markSendStarted(OutboxMessage<String> m) { ... }
  @Override public void update(OutboxMessage<String> message) { ... }
  @Override public int purgePublished(Instant cutoff, int batchSize) { ... }
  @Override public long countFailed() { ... }
  @Override public int requeue(Collection<UUID> ids) { ... }
  @Override public int requeueFailed(String key) { ... }
}
```

## Properties

The pre-6.0 keys are no longer honoured: they are not copied to their new names, no deprecation
warning is logged, and the metadata no longer lists them. A key still set under its old name is
silently ignored and its default applies.

| Old key (ignored since 9.0) | Key |
|---|---|
| `outbox.enabled` | `codehunters.outbox.enabled` |
| `outbox.batch-size` | `codehunters.outbox.batch-size` |
| `outbox.max-attempts` | `codehunters.outbox.max-attempts` |
| `outbox.task.*` | `codehunters.outbox.task.*` |
| `outbox.claim.ttl-ms` | `codehunters.outbox.claim.ttl` |
| `outbox.backoff.base-delay-ms`, `outbox.backoff.max-delay-ms` | `codehunters.outbox.backoff.base-delay`, `codehunters.outbox.backoff.max-delay` |
| `outbox.publisher.*` | `codehunters.outbox.publisher.*` |
| `outbox.purge.*` | `codehunters.outbox.purge.*` |

The same applies to environment variables (`OUTBOX_BATCH_SIZE` → `CODEHUNTERS_OUTBOX_BATCH_SIZE`).

`codehunters.outbox.claim.dialect` (and `outbox.claim.dialect`) is removed. Delete it; a leftover
value is ignored.

## Removed API

| 8.x | 9.0 |
|---|---|
| `io.codehunters.messaging.jdbc.OutboxDialect`, `OutboxDialectResolver`, `PostgresOutboxDialect`, `GenericOutboxDialect` and the non-PostgreSQL dialect | Removed; the adapter holds the PostgreSQL SQL |
| `OutboxDialect` bean | No longer registered |
| `io.codehunters.messaging.config.OutboxLegacyProperties` | Removed with the `outbox.*` aliases |
| `OutboxRepositoryPort`, bean `outboxRepositoryPort` | `OutboxWriter` + `OutboxDispatchStore`, bean `outboxJdbcAdapter` (see [Port split](#port-split)) |
| `OutboxEventFactory.create(topic, traceId, aggregate, eventName, eventType, payload)`, both `createRoutingKey(...)` | `OutboxEventFactory.create(OutboxEvent)` |
| `new OutboxEventFactory(origin, eventMessageFactory)`, `new OutboxEventFactory(origin, traceIdSource)` | `new OutboxEventFactory(eventMessageFactory)` |
| `new EventMessageFactory(origin)`, `new EventMessageFactory(origin, traceIdSource)` | `new EventMessageFactory(origin, traceIdSource, clock)` |
| `EventMessageFactory.create(traceId, [locale,] aggregate, eventName, eventType, payload)` | `EventMessageFactory.create(OutboxEvent)` |
| `EventMessage.builder()`, `Header.builder()`, `Body.builder()`, getters and setters | Record constructors and accessors |
| `OutboxMessage` getters and setters | Record accessors, `builder()` / `toBuilder()` and transition methods |
| `SendOutboxMessagesUseCase(repository, publisher, batchSize, maxAttempts, instanceId, origin, claimTtlMs, baseBackoffMs, maxBackoffMs[, clock])` | `SendOutboxMessagesUseCase(store, publisher, DispatchSettings)` |
| `new OutboxAdapterSettings(origin, dialect, clock)` | `new OutboxAdapterSettings(origin, clock, trigger)` |
| `OutboxAdapterSettings.defaults(dataSource, origin)` | `OutboxAdapterSettings.defaults(origin)` (no dispatch on commit) |
| `EventMessagingProperties.Claim#dialect()`, `Claim#ttlMs()`, `Backoff#baseDelayMs()`, `Backoff#maxDelayMs()` | `Claim#ttl()`, `Claim#maxPerKey()`, `Backoff#baseDelay()`, `Backoff#maxDelay()` |
| `codehunters.outbox.claim.ttl-ms`, `backoff.base-delay-ms`, `backoff.max-delay-ms` | `claim.ttl`, `backoff.base-delay`, `backoff.max-delay` |
| `OutboxScheduler.processOutboxMessages()`, `OutboxPurgeScheduler.purgePublished()` | `run()` |
| `new OutboxPurgeScheduler(OutboxRepositoryPort, settings)`, `new OutboxFailedGauge(OutboxRepositoryPort, origin, clock)` | Take an `OutboxDispatchStore` |
| `@EnableScheduling`, `@EnableSchedulerLock` added by `-jdbc` | Not added (see [Scheduling](#scheduling)) |
| Application `Clock` bean used by the outbox | Bean `outboxClock` (see [Clock](#clock)) |

`OutboxJdbcAdapter(dataSource, outboxJsonMapper, origin)` and
`OutboxJdbcAdapter(dataSource, outboxJsonMapper, settings)` keep their shape.

## Added API

| 9.0 | Use |
|---|---|
| `OutboxMessage#traceparent()` / `builder().traceparent(...)` | W3C context stored with the row |
| `TraceIdSource#currentTraceParent()` | Default method, empty; implement it in a custom `TraceIdSource` to store the context |
| `MessagePublisherPort.TRACEPARENT_HEADER`, `LEGACY_TRACEPARENT_HEADER` | `traceparent`, `x-traceparent` |
| `EventMessagingProperties.Publisher#legacyTraceparentHeader()` | `codehunters.outbox.publisher.legacy-traceparent-header` |
| `RabbitMessagePublisherAdapter(ops, confirmTimeout, legacyTraceparentHeader)`, `KafkaMessagePublisherAdapter(template, sendTimeout, legacyTraceparentHeader)` | The two-argument constructors remain and send `traceparent` only |
| `io.codehunters.messaging.tracing.TracedMessagePublisher`, bean `outboxTraceContextDecorator` | Registered when a Micrometer `Tracer` bean exists |
| `OutboxEvent` (`to(...).key(...).type(...).payload(...)`, copy methods) | Describes an event to write |
| `OutboxWriter` | `save`, the application-facing port |
| `OutboxDispatchStore`, `ClaimRequest` | Claim, mark, update, purge, count, `requeue(ids)`, `requeueFailed(key)` |
| `OutboxDispatchTrigger` (`none()`), `dispatch.CoalescingDispatchTrigger`, bean `outboxDispatchTrigger` | Dispatch on commit |
| `dispatch.DispatchSettings`, `dispatch.RetryPolicy` | Use case settings |
| `SendOutboxMessagesPort.execute()` returning `boolean` | `true` when the claim came back full |
| `OutboxMessage.sendStarted`, `published`, `attemptFailed`, `retryAt`, `failed`, `released` | Immutable state transitions |
| `EventMessage.Header.DEFAULT_VERSION` | `1.0.0` |
| `jdbc.scheduler.OutboxScheduledTask`, `OutboxTaskSchedule`, `OutboxTaskLock`, `OutboxSchedulingLifecycle`; beans `outboxTaskScheduler`, `outboxDispatchTask`, `outboxPurgeTask`, `outboxSchedulingLifecycle` | Private, programmatically locked scheduling |
| `OutboxScheduler(port, trigger)` | Requests another dispatch when a poll's claim came back full |
| Bean `outboxClock` | The outbox clock |
| `codehunters.outbox.claim.max-per-key`, `codehunters.outbox.scheduling.dispatch-on-commit` | See [New keys](#new-keys) |

A custom `TraceIdSource` bean that only implements `currentTraceId()` stores no context; add
`currentTraceParent()` to keep trace continuity.

## Known limits

- Maven does not apply the Gradle capability conflict check: import `io.codehunters:sdk-bom` to keep
  the `sdk-event-messaging-*` artifacts on one version.
- `requeue(ids)` sends all ids in one `IN (...)` list; chunk very large lists.
- Commit-triggered dispatch runs on the instance that committed, without the ShedLock dispatch lock;
  several instances may dispatch at the same time, on disjoint rows.
