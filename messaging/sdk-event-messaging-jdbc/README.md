# sdk-event-messaging-jdbc

JDBC store for the transactional outbox: the `outbox_message` repository, claim dialects, and the
ShedLock-protected dispatch and purge schedulers. Model and dispatch semantics are described in
[`sdk-event-messaging-core`](../sdk-event-messaging-core/README.md).

```kotlin
implementation("io.codehunters:sdk-event-messaging-jdbc:6.0.0")
implementation("io.codehunters:sdk-event-messaging-rabbit:6.0.0") // or -kafka
```

Brings `-core` and ShedLock (`shedlock-spring`, `shedlock-provider-jdbc-template`). The application
supplies a `DataSource` (`spring-boot-starter-jdbc`) and Jackson 3 (`tools.jackson`).

## What it registers

| Bean | Condition | Notes |
|---|---|---|
| `outboxJsonMapper` | always | Serializes payloads; not a default candidate, so the application's `JsonMapper` stays the only by-type match |
| `OutboxDialect` | `DataSource` bean | From `claim.dialect` |
| `OutboxRepositoryPort` (`OutboxJdbcAdapter`) | `DataSource` bean | |
| `LockProvider` (`JdbcTemplateLockProvider`, DB time) | `DataSource` bean | Table `shedlock` |
| `OutboxScheduler` | `SendOutboxMessagesPort` bean (a transport is active) and `scheduling.enabled` | Dispatch every `task.fixed-delay` |
| `OutboxPurgeScheduler` | repository bean and `scheduling.enabled` | Purge every `purge.fixed-delay` |
| `OutboxFailedGauge` | Micrometer on the classpath | `outbox_failed_messages{origin}` |

Every bean backs off when the application defines its own. The module turns on `@EnableScheduling`
and `@EnableSchedulerLock` (default lock-at-most `60s`) whenever `codehunters.outbox.enabled` and
`codehunters.outbox.scheduling.enabled` are `true` (both default), which also activates any other
`@Scheduled` method in the application.

Set `codehunters.outbox.scheduling.enabled=false` when the application runs dispatch itself or
another process does: the two schedulers and the scheduling annotations are left out, while the
repository, `SendOutboxMessagesPort` and `LockProvider` stay. Call `SendOutboxMessagesPort.execute()`
from your own trigger, one instance at a time (ShedLock or equivalent), and purge `PUBLISHED` rows
yourself or in the process that keeps scheduling on.

## Dialects

| `claim.dialect` | Database | Claim |
|---|---|---|
| `auto` (default) | Read once from the JDBC metadata | PostgreSQL, Microsoft SQL Server and H2 are recognised; any other product fails startup |
| `postgresql` | PostgreSQL | `FOR UPDATE SKIP LOCKED` |
| `sqlserver` | SQL Server | `UPDLOCK, READPAST` with `OUTPUT` |
| `generic` | Others (H2, tests) | Plain `UPDATE`; not atomic under concurrent dispatchers, rely on ShedLock |

An unknown value, or `auto` with an unsupported or unreadable database, fails startup naming
`codehunters.outbox.claim.dialect`. Every dialect counts a stale `IN_FLIGHT` reclaim as an attempt
and skips a row while an earlier row with the same `(origin, topic, message_key)` is `PENDING`,
`IN_FLIGHT` or `FAILED`.

## DDL

The SDK never runs DDL. Copy the reference files from the jar into your changelog and adapt the
changeset author and id; including them from the jar would change checksums on every upgrade.

| File | Use |
|---|---|
| `db/migration/outbox/postgresql/outbox_message_create.sql` | PostgreSQL: `outbox_message` with `seq`, indexes, `shedlock` |
| `db/migration/outbox/outbox_message_create.sql` | SQL Server: same, for new installs |
| `db/migration/outbox/sqlserver/outbox_message_add_seq.sql` | SQL Server tables created before 6.0 |

**SQL Server:** apply `outbox_message_add_seq.sql` **before** deploying 6.0. The claim orders by
`seq`; without the column every dispatch fails. Existing rows get `seq` in physical order, so drain
the outbox first if the order of queued rows matters.

## Configuration

Keys under `codehunters.outbox`; the rest are in the [core README](../sdk-event-messaging-core/README.md).

| Key | Default | Notes |
|---|---|---|
| `claim.dialect` | `auto` | `auto`, `postgresql`, `sqlserver`, `generic` |
| `scheduling.enabled` | `true` | `false` drops the dispatch and purge schedulers and `@EnableScheduling` / `@EnableSchedulerLock` |
| `task.fixed-delay` | `5000` | ms between dispatch ticks |
| `task.lock-name` | `outbox-${spring.application.name}` | ShedLock name |
| `task.lock-at-most` | `60000` | ms |
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
| Unsupported database with `auto`, unknown dialect | Startup fails |
| Dispatch or purge throws | Logged with the exception class only; the next tick runs normally |
| Instance dies with a claimed batch | Rows are reclaimed after `claim.ttl-ms`, counting one attempt |
| `outbox_message` lacks `seq` | Every claim fails until the DDL is applied |

## Migrating from 5.x

Replace `sdk-event-messaging` with this artifact plus a transport, rename `outbox.task.*` /
`outbox.purge.*` / `outbox.claim.*` to `codehunters.outbox.*`, and apply the DDL above. Details in
[MIGRATION-6.0.md](../MIGRATION-6.0.md).
