# sdk-event-messaging-jdbc

PostgreSQL store for the transactional outbox: the `outbox_message` adapter, dispatch on commit,
and the ShedLock-protected dispatch poll and purge, run on a private scheduler. Model and dispatch
semantics are described in [`sdk-event-messaging-core`](../sdk-event-messaging-core/README.md).

```kotlin
implementation("io.codehunters:sdk-event-messaging-jdbc:9.0.0")
implementation("io.codehunters:sdk-event-messaging-rabbit:9.0.0") // or -kafka
```

Brings `-core` and ShedLock (`shedlock-spring`, `shedlock-provider-jdbc-template`). The application
supplies a PostgreSQL `DataSource` (`spring-boot-starter-jdbc` and the PostgreSQL driver) and
Jackson 3 (`tools.jackson`).

## What it registers

| Bean | Condition | Notes |
|---|---|---|
| `outboxJsonMapper` | always | Serializes payloads; built from the application's Jackson setup (see [JSON](#json)). Not a default candidate, so the application's `JsonMapper` stays the only by-type match |
| `outboxJdbcAdapter` (`OutboxJdbcAdapter`: `OutboxWriter` and `OutboxDispatchStore`) | `DataSource` bean, and neither port defined by the application | |
| `LockProvider` (`JdbcTemplateLockProvider`, DB time) | `DataSource` bean | Table `shedlock` |
| `outboxTaskScheduler` (`ThreadPoolTaskScheduler`, 3 threads `outbox-*`) | `scheduling.enabled` | Not a default candidate: lookups of `TaskScheduler` by type do not see it |
| `outboxDispatchTrigger` | `scheduling.enabled`, `scheduling.dispatch-on-commit` and a `SendOutboxMessagesPort` (a transport is active) | Runs a dispatch on `outboxTaskScheduler` after each commit that saved outbox rows |
| `outboxScheduler` (`OutboxScheduler`), `outboxDispatchTask` | `scheduling.enabled` and a `SendOutboxMessagesPort` | Dispatch poll every `task.fixed-delay` |
| `outboxPurgeScheduler` (`OutboxPurgeScheduler`), `outboxPurgeTask` | `scheduling.enabled` | Purge every `purge.fixed-delay` |
| `outboxSchedulingLifecycle` | `scheduling.enabled` | Schedules every `OutboxScheduledTask` bean on `outboxTaskScheduler`, each run under its ShedLock lock |
| `OutboxFailedGauge` | Micrometer on the classpath and an `OutboxDispatchStore` | `outbox_failed_messages{origin}`; the count is cached for 30 s (`outboxClock`), so scrapes do not each query the table |

The scheduler, task and lifecycle beans need an `OutboxDispatchStore` and a `LockProvider`. Every
bean backs off when the application defines its own.

## Scheduling

The module adds neither `@EnableScheduling` nor `@EnableSchedulerLock`: the application's own
`@Scheduled` and `@SchedulerLock` methods run only if the application enables them itself, and its
task scheduler is never used. `outboxSchedulingLifecycle` schedules the dispatch poll (first run at
startup, then every `task.fixed-delay`) and the purge (`purge.initial-delay`, then every
`purge.fixed-delay`) on `outboxTaskScheduler`, and wraps each run in ShedLock through the
`LockProvider` bean, so one instance polls or purges at a time. `OutboxScheduler` and
`OutboxPurgeScheduler` are plain `Runnable`s that log failures by exception class and never throw.

**Dispatch on commit.** When `save` (or a requeue) runs inside a transaction, the adapter registers
one synchronization per transaction that asks `outboxDispatchTrigger` for a dispatch after the
commit; outside a transaction it asks right away. Requests coalesce: at most one run is pending
while one runs, and a run whose claim came back full runs again, as does the poll's. These runs do
not take the ShedLock dispatch lock: concurrent claimers on several instances receive disjoint rows
(`SKIP LOCKED`), updates are fenced by the claim marker, and per-key order is kept by the claim. The
poll stays as a safety net.

To replace the scheduler, define a bean named `outboxTaskScheduler`: a `TaskScheduler` that, while
`scheduling.dispatch-on-commit` is on, is also an `Executor` (`ThreadPoolTaskScheduler` is both).

Set `codehunters.outbox.scheduling.enabled=false` when the application runs dispatch itself or
another process does: the scheduler, the trigger, the poll and the purge are left out, while the
adapter, `SendOutboxMessagesPort` and `LockProvider` stay. Call `SendOutboxMessagesPort.execute()`
from your own trigger (repeat while it returns `true` to drain a backlog), one instance at a time
(ShedLock or equivalent), and purge `PUBLISHED` rows yourself or in the process that keeps
scheduling on.

## Throughput

Measured on PostgreSQL 17 (Testcontainers) with a 4-connection pool and an in-memory publisher:
commit to publish takes about 2.5 ms on average, and a 2000-row keyed backlog drains at about 1100
msg/s with `claim.max-per-key=1` and about 1490 msg/s with the default `10`. 8.x published at most
50 rows per 5 s poll (about 10 msg/s, 3 to 5 msg/s with its 10 s `task.lock-at-least`), one row per
key per poll. With a real broker a run sends sequentially, so one instance publishes about one
message per acknowledgement latency (a 2 ms ack gives about 500 msg/s).

## Database

PostgreSQL only. The claim is one statement: it locks the candidate rows with `FOR UPDATE SKIP
LOCKED`, so concurrent dispatchers receive disjoint rows, marks them `IN_FLIGHT` and returns them.
A stale `IN_FLIGHT` reclaim counts as an attempt only when its send had started. Up to
`claim.max-per-key` consecutive rows of one `(origin, topic, message_key)` are claimed together; a
row is skipped while an earlier row with the same key is `FAILED`, `PENDING` but not yet due, or
`IN_FLIGHT` under another live claim. At startup the SDK reads the database product name and fails
when it is not PostgreSQL; when the database cannot be reached at startup the check is skipped with
a warning.

The check reads the JDBC driver's product name (`PostgreSQL` from pgjdbc), not the server's: Amazon
Redshift, CockroachDB or YugabyteDB through pgjdbc pass it but may fail at the first claim, which
needs `FOR UPDATE ... SKIP LOCKED`, and a vendor driver that reports another name (EDB's
`EnterpriseDB`) is rejected.

Tests that run the outbox on an embedded H2 fail at startup. Set `codehunters.outbox.enabled=false`
in the test profile, use a Testcontainers PostgreSQL, or declare a test bean implementing both
`OutboxWriter` and `OutboxDispatchStore` (the JDBC adapter and its check back off). See
[MIGRATION-9.0](../MIGRATION-9.0.md#tests-on-h2).

## Requeue

`requeue(ids)` and `requeueFailed(key)` put `FAILED` rows of this origin back to `PENDING`, due now,
with `attempts = 0` and no error, claim or send mark, and request a dispatch after the commit (see
the [core README](../sdk-event-messaging-core/README.md#dispatch-semantics)). `requeue` binds the ids
into one `IN (...)` list; chunk very large lists.

## JSON

`outboxJsonMapper` is built from the application's `JsonMapper.Builder` when Boot's Jackson
auto-configuration provides one, so `spring.jackson.*`, `JsonMapperBuilderCustomizer` beans and
modules apply to the stored message, envelope included. Indented output is always off and dates
are always ISO-8601 strings. Without Boot's Jackson it is a plain `JsonMapper` with those two
settings. Define a bean named `outboxJsonMapper` to pin the format independently of the
application.

## DDL

The SDK never runs DDL. Copy the reference files from the jar into your changelog and adapt the
changeset author and id; including them from the jar would change checksums on every upgrade.

| File | Use |
|---|---|
| `db/migration/outbox/postgresql/outbox_message_create.sql` | `outbox_message` with `seq`, `traceparent`, indexes, `shedlock` |
| `db/migration/outbox/postgresql/outbox_message_add_send_started_at.sql` | Tables created by 6.x |
| `db/migration/outbox/postgresql/outbox_message_add_traceparent.sql` | Tables created by 6.x (after the line above), 7.x or 8.x |

**9.0:** apply `outbox_message_add_traceparent.sql` **before** deploying 9.0. `save` writes and the
claim returns `traceparent` (`VARCHAR(55)`, nullable), the W3C trace context stored with each row
and sent as the `traceparent` broker header; without it saving and dispatch fail. 8.x ignores the
column, and the script is idempotent (`ADD COLUMN IF NOT EXISTS`)
([MIGRATION-9.0](../MIGRATION-9.0.md#traceparent-column)).

**7.0:** apply `outbox_message_add_send_started_at.sql` **before** deploying 7.0. The claim reads and
clears `send_started_at`, the mark written before each publish; without it every claim fails. The
column is nullable and ignored by 6.x ([MIGRATION-7.0](../MIGRATION-7.0.md)).

**Transactions:** `SendOutboxMessagesPort.execute()` must run outside any caller transaction. The
send mark has to commit before the broker call; inside a transaction it commits only afterwards,
and a crash in between leaves the row uncharged. The adapter warns once when a mark runs inside
one. `save` is the opposite: it joins the caller's transaction on purpose.

## Configuration

Keys under `codehunters.outbox`; the rest are in the [core README](../sdk-event-messaging-core/README.md).
Durations accept `30s`, `5m` or ISO-8601 (`PT5M`); a plain number means milliseconds.

| Key | Default | Notes |
|---|---|---|
| `scheduling.enabled` | `true` | `false` drops the outbox scheduler, the trigger, the poll and the purge |
| `scheduling.dispatch-on-commit` | `true` | `false` leaves dispatch to the poll |
| `task.fixed-delay` | `5s` | Between dispatch polls; > 0 |
| `task.lock-name` | unset | ShedLock name; `outbox-<spring.application.name>` when unset |
| `task.lock-at-most` | `5m` | Must exceed `claim.ttl / 2` + the transport timeout (startup fails otherwise) |
| `task.lock-at-least` | `0s` | ≥ 0 and ≤ `task.lock-at-most` (was `10000` ms in 8.x) |
| `purge.retention` | `7d` | `PUBLISHED` rows older than this are deleted; > 0 |
| `purge.batch-size` | `500` | Rows per delete statement; repeated until a batch comes back short |
| `purge.fixed-delay` | `1h` | Between purge runs; > 0 |
| `purge.initial-delay` | `1m` | Before the first run; ≥ 0 |
| `purge.lock-name` | unset | `outbox-purge-<spring.application.name>` when unset |
| `purge.lock-at-most` | `10m` | > 0 |
| `purge.lock-at-least` | `30s` | ≥ 0 and ≤ `purge.lock-at-most` |

The purge only deletes `PUBLISHED` rows of this origin. `PENDING`, `IN_FLIGHT` and `FAILED` rows
are never touched.

## Failure semantics

| Situation | Result |
|---|---|
| Dispatch or purge throws | Logged with the exception class only; the next run proceeds normally |
| Lock provider fails | Logged with the exception class only; the run is skipped |
| Instance dies with a claimed batch | Rows are reclaimed after `claim.ttl`; only rows whose send had started count one attempt |
| A commit-time dispatch request is lost | The next poll picks the rows up |
| `outbox_message` lacks `seq` | Every claim fails until the DDL is applied |

## Packages

| Package | Contents |
|---|---|
| `io.codehunters.messaging.jdbc` | `OutboxJdbcAdapter`, `OutboxAdapterSettings` |
| `io.codehunters.messaging.jdbc.scheduler` | `OutboxScheduler`, `OutboxPurgeScheduler`, `OutboxScheduledTask`, `OutboxTaskSchedule`, `OutboxTaskLock`, `OutboxSchedulingLifecycle` |
| `io.codehunters.messaging.jdbc.metrics` | `OutboxFailedGauge` |
| `io.codehunters.messaging.jdbc.config` | Auto-configuration |

## Migrating from 8.x

The store supports PostgreSQL only, `claim.dialect` and the dialect classes are gone, the old
`outbox.*` keys are ignored, and `outbox_message` needs the `traceparent` column before deploying.
The `outboxRepositoryPort` bean is now `outboxJdbcAdapter` (`OutboxWriter` and
`OutboxDispatchStore`), the timing keys are durations, `task.lock-at-least` defaults to `0`, and
the module no longer turns on `@EnableScheduling` / `@EnableSchedulerLock`; see
[MIGRATION-9.0.md](../MIGRATION-9.0.md).

## Migrating from 7.x

Only imports change; see [MIGRATION-8.0.md](../MIGRATION-8.0.md), then
[MIGRATION-9.0.md](../MIGRATION-9.0.md).

## Migrating from 5.x

Replace `sdk-event-messaging` with this artifact plus a transport, rename `outbox.task.*` /
`outbox.purge.*` to `codehunters.outbox.*` and `outbox.claim.ttl-ms` to `codehunters.outbox.claim.ttl`,
and apply the DDL above. Details in [MIGRATION-6.0.md](../MIGRATION-6.0.md), then
[MIGRATION-9.0.md](../MIGRATION-9.0.md).
