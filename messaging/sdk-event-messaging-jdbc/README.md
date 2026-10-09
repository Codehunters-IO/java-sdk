# sdk-event-messaging-jdbc

PostgreSQL store for the transactional outbox: the `outbox_message` repository and the
ShedLock-protected dispatch and purge schedulers. Model and dispatch semantics are described in
[`sdk-event-messaging-core`](../sdk-event-messaging-core/README.md).

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
| `outboxJsonMapper` | always | Serializes payloads; not a default candidate, so the application's `JsonMapper` stays the only by-type match |
| `OutboxRepositoryPort` (`OutboxJdbcAdapter`) | `DataSource` bean | |
| `LockProvider` (`JdbcTemplateLockProvider`, DB time) | `DataSource` bean | Table `shedlock` |
| `OutboxScheduler` | `SendOutboxMessagesPort` bean (a transport is active) and `scheduling.enabled` | Dispatch every `task.fixed-delay` |
| `OutboxPurgeScheduler` | repository bean and `scheduling.enabled` | Purge every `purge.fixed-delay` |
| `OutboxFailedGauge` | Micrometer on the classpath | `outbox_failed_messages{origin}`; the count is cached for 30 s (outbox `Clock`), so scrapes do not each query the table |

Every bean backs off when the application defines its own. The module turns on `@EnableScheduling`
and `@EnableSchedulerLock` (default lock-at-most `60s`) whenever `codehunters.outbox.enabled` and
`codehunters.outbox.scheduling.enabled` are `true` (both default), which also activates any other
`@Scheduled` method in the application.

Set `codehunters.outbox.scheduling.enabled=false` when the application runs dispatch itself or
another process does: the two schedulers and the scheduling annotations are left out, while the
repository, `SendOutboxMessagesPort` and `LockProvider` stay. Call `SendOutboxMessagesPort.execute()`
from your own trigger, one instance at a time (ShedLock or equivalent), and purge `PUBLISHED` rows
yourself or in the process that keeps scheduling on.

## Database

PostgreSQL only. The claim is one statement: it locks the candidate rows with `FOR UPDATE SKIP
LOCKED`, so concurrent dispatchers receive disjoint rows, marks them `IN_FLIGHT` and returns them.
A stale `IN_FLIGHT` reclaim counts as an attempt only when its send had started, and a row is
skipped while an earlier row with the same `(origin, topic, message_key)` is `PENDING`, `IN_FLIGHT`
or `FAILED`. At startup the SDK reads the database product name and fails when it is not
PostgreSQL; when the database cannot be reached at startup the check is skipped with a warning.

## DDL

The SDK never runs DDL. Copy the reference files from the jar into your changelog and adapt the
changeset author and id; including them from the jar would change checksums on every upgrade.

| File | Use |
|---|---|
| `db/migration/outbox/postgresql/outbox_message_create.sql` | `outbox_message` with `seq`, indexes, `shedlock` |
| `db/migration/outbox/postgresql/outbox_message_add_send_started_at.sql` | Tables created by 6.x |

**7.0:** apply `outbox_message_add_send_started_at.sql` **before** deploying 7.0. The claim reads and
clears `send_started_at`, the mark written before each publish; without it every claim fails. The
column is nullable and ignored by 6.x ([MIGRATION-7.0](../MIGRATION-7.0.md)).

**Transactions:** `SendOutboxMessagesPort.execute()` must run outside any caller transaction. The
send mark has to commit before the broker call; inside a transaction it commits only afterwards,
and a crash in between leaves the row uncharged. The adapter warns once when a mark runs inside
one. `save` is the opposite: it joins the caller's transaction on purpose.

## Configuration

Keys under `codehunters.outbox`; the rest are in the [core README](../sdk-event-messaging-core/README.md).

| Key | Default | Notes |
|---|---|---|
| `scheduling.enabled` | `true` | `false` drops the dispatch and purge schedulers and `@EnableScheduling` / `@EnableSchedulerLock` |
| `task.fixed-delay` | `5000` | ms between dispatch ticks |
| `task.lock-name` | `outbox-${spring.application.name}` | ShedLock name |
| `task.lock-at-most` | `300000` | ms, ISO-8601 (`PT5M`) or `5m`; must exceed `claim.ttl-ms / 2` + the transport timeout (startup fails otherwise) |
| `task.lock-at-least` | `10000` | ms |
| `purge.retention` | `7d` | `PUBLISHED` rows older than this are deleted; > 0 |
| `purge.batch-size` | `500` | Rows per delete statement; repeated until a batch comes back short |
| `purge.fixed-delay` | `3600000` | ms between purge runs |
| `purge.initial-delay` | `60000` | ms before the first run |
| `purge.lock-name` | `outbox-purge-${spring.application.name}` | |
| `purge.lock-at-most` | `PT10M` | |
| `purge.lock-at-least` | `PT30S` | |

The purge only deletes `PUBLISHED` rows of this origin. `PENDING`, `IN_FLIGHT` and `FAILED` rows
are never touched.

## Failure semantics

| Situation | Result |
|---|---|
| Dispatch or purge throws | Logged with the exception class only; the next tick runs normally |
| Instance dies with a claimed batch | Rows are reclaimed after `claim.ttl-ms`; only rows whose send had started count one attempt |
| `outbox_message` lacks `seq` | Every claim fails until the DDL is applied |

## Packages

| Package | Contents |
|---|---|
| `io.codehunters.messaging.jdbc` | `OutboxJdbcAdapter`, `OutboxAdapterSettings` |
| `io.codehunters.messaging.jdbc.scheduler` | Dispatch and purge schedulers |
| `io.codehunters.messaging.jdbc.metrics` | `OutboxFailedGauge` |
| `io.codehunters.messaging.jdbc.config` | Auto-configuration |

## Migrating from 8.x

The store supports PostgreSQL only, `claim.dialect` and the dialect classes are gone, and the old
`outbox.*` keys are ignored; see [MIGRATION-9.0.md](../MIGRATION-9.0.md).

## Migrating from 7.x

Only imports change; see [MIGRATION-8.0.md](../MIGRATION-8.0.md), then
[MIGRATION-9.0.md](../MIGRATION-9.0.md).

## Migrating from 5.x

Replace `sdk-event-messaging` with this artifact plus a transport, rename `outbox.task.*` /
`outbox.purge.*` / `outbox.claim.ttl-ms` to `codehunters.outbox.*`, and apply the DDL above. Details in
[MIGRATION-6.0.md](../MIGRATION-6.0.md).
