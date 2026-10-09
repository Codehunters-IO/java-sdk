# Migrating sdk-event-messaging to 9.0

9.0 makes the outbox PostgreSQL-only and drops the pre-6.0 property names. The four artifacts
(`-core`, `-jdbc`, `-rabbit`, `-kafka`) move to 9.0.0 together; import the matching
`io.codehunters:sdk-bom` to keep them aligned. Java packages, beans, the `codehunters.outbox.*`
keys (except `claim.dialect`), the dispatch semantics and the PostgreSQL DDL do not change.

**Coming from 7.x?** Apply [MIGRATION-8.0.md](MIGRATION-8.0.md) first (imports only). From 6.x,
start with [MIGRATION-7.0.md](MIGRATION-7.0.md) (the `send_started_at` column); from 5.x, with
[MIGRATION-6.0.md](MIGRATION-6.0.md).

## Checklist

1. Make sure the outbox runs on PostgreSQL. Other databases are no longer supported.
2. Rename any remaining `outbox.*` key to `codehunters.outbox.*`; the old names are ignored now.
3. Remove `codehunters.outbox.claim.dialect` from your configuration.
4. Recompile if you build `OutboxJdbcAdapter` or `OutboxAdapterSettings` yourself, or reference a
   dialect class (see [Removed API](#removed-api)).
5. Bump the dependencies (or the BOM) to 9.0.0.

## PostgreSQL only

The adapter issues the PostgreSQL claim (`FOR UPDATE SKIP LOCKED` with `RETURNING`) and purge
directly; there is no dialect to choose any more. Other databases are no longer supported, and the
SDK no longer reads the database product name at startup: on another database the first claim
fails with a SQL error instead of startup. Only the PostgreSQL reference scripts remain in the
`-jdbc` jar:

| File | Use |
|---|---|
| `db/migration/outbox/postgresql/outbox_message_create.sql` | New installs: `outbox_message` with `seq`, indexes, `shedlock` |
| `db/migration/outbox/postgresql/outbox_message_add_send_started_at.sql` | Tables created by 6.x |

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
| `outbox.claim.ttl-ms` | `codehunters.outbox.claim.ttl-ms` |
| `outbox.backoff.*` | `codehunters.outbox.backoff.*` |
| `outbox.publisher.*` | `codehunters.outbox.publisher.*` |
| `outbox.purge.*` | `codehunters.outbox.purge.*` |

The same applies to environment variables (`OUTBOX_BATCH_SIZE` → `CODEHUNTERS_OUTBOX_BATCH_SIZE`).

`codehunters.outbox.claim.dialect` (and `outbox.claim.dialect`) is removed. Delete it; a leftover
value is ignored.

## Removed API

| 8.x | 9.0 |
|---|---|
| `io.codehunters.messaging.jdbc.OutboxDialect`, `OutboxDialectResolver`, `PostgresOutboxDialect`, `GenericOutboxDialect` and the non-PostgreSQL dialect | Removed; the adapter holds the PostgreSQL SQL |
| `new OutboxAdapterSettings(origin, dialect, clock)` | `new OutboxAdapterSettings(origin, clock)` |
| `OutboxAdapterSettings.defaults(dataSource, origin)` | `OutboxAdapterSettings.defaults(origin)` |
| `EventMessagingProperties.Claim#dialect()` | Removed; `Claim` keeps `ttlMs()` |
| `io.codehunters.messaging.config.OutboxLegacyProperties` | Removed with the `outbox.*` aliases |
| `OutboxDialect` bean | No longer registered |

`OutboxJdbcAdapter(dataSource, outboxJsonMapper, origin)` and
`OutboxJdbcAdapter(dataSource, outboxJsonMapper, settings)` keep their shape.

## Known limits

- Maven does not apply the Gradle capability conflict check: import `io.codehunters:sdk-bom` to keep
  the `sdk-event-messaging-*` artifacts on one version.
