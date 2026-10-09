# Migrating sdk-event-messaging to 9.0

9.0 makes the outbox PostgreSQL-only, drops the pre-6.0 property names and stores the request's
trace context with each row so the published message continues it. The four artifacts (`-core`,
`-jdbc`, `-rabbit`, `-kafka`) move to 9.0.0 together; import the matching `io.codehunters:sdk-bom`
to keep them aligned. Java packages, beans, the `codehunters.outbox.*` keys (except
`claim.dialect`) and the dispatch semantics do not change; the PostgreSQL DDL gains one nullable
column.

**Coming from 7.x?** Apply [MIGRATION-8.0.md](MIGRATION-8.0.md) first (imports only). From 6.x,
start with [MIGRATION-7.0.md](MIGRATION-7.0.md) (the `send_started_at` column); from 5.x, with
[MIGRATION-6.0.md](MIGRATION-6.0.md).

## Checklist

1. Make sure the outbox runs on PostgreSQL. Other databases are no longer supported.
2. **Before deploying 9.0**, add the `traceparent` column to `outbox_message` (see
   [traceparent column](#traceparent-column)).
3. Rename any remaining `outbox.*` key to `codehunters.outbox.*`; the old names are ignored now.
4. Remove `codehunters.outbox.claim.dialect` from your configuration.
5. Recompile if you build `OutboxJdbcAdapter` or `OutboxAdapterSettings` yourself, or reference a
   dialect class (see [Removed API](#removed-api)).
6. Fix tests that run the outbox on an embedded H2 (see [Tests on H2](#tests-on-h2)).
7. If a consumer reads only `x-traceparent`, set
   `codehunters.outbox.publisher.legacy-traceparent-header=true` (see
   [Trace continuity](#trace-continuity)).
8. Bump the dependencies (or the BOM) to 9.0.0.

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
- With Micrometer Tracing and a `Tracer` bean, each publish runs with the stored context as the
  current one (`TracedMessagePublisher`): log lines written while publishing carry the request's
  trace id, and spans started there parent on it.

The SDK-owned `outboxRabbitTemplate` and `outboxKafkaTemplate` keep Boot's observation off; the
header comes from the adapter, not from the dispatching thread.

## Tests on H2

An embedded H2 now fails startup with "The outbox supports PostgreSQL only, but the data source is
H2", even in `MODE=PostgreSQL` (the driver still reports H2). In `@SpringBootTest` or slice tests
that load the outbox auto-configuration, pick one:

- set `codehunters.outbox.enabled=false` in the test profile when the test does not exercise the
  outbox;
- run the test against PostgreSQL with Testcontainers;
- declare your own `OutboxRepositoryPort` test bean (for example an in-memory fake): the SDK backs
  off its JDBC repository, and the check, when one exists.

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

## Added API

| 9.0 | Use |
|---|---|
| `OutboxMessage#getTraceparent()` / `traceparent(...)` | W3C context stored with the row |
| `TraceIdSource#currentTraceParent()` | Default method, empty; implement it in a custom `TraceIdSource` to store the context |
| `MessagePublisherPort.TRACEPARENT_HEADER`, `LEGACY_TRACEPARENT_HEADER` | `traceparent`, `x-traceparent` |
| `EventMessagingProperties.Publisher#legacyTraceparentHeader()` | `codehunters.outbox.publisher.legacy-traceparent-header` |
| `RabbitMessagePublisherAdapter(ops, confirmTimeout, legacyTraceparentHeader)`, `KafkaMessagePublisherAdapter(template, sendTimeout, legacyTraceparentHeader)` | The two-argument constructors remain and send `traceparent` only |
| `io.codehunters.messaging.tracing.TracedMessagePublisher`, bean `outboxTraceContextDecorator` | Registered when a Micrometer `Tracer` bean exists |

A custom `TraceIdSource` bean that only implements `currentTraceId()` stores no context; add
`currentTraceParent()` to keep trace continuity.

## Known limits

- Maven does not apply the Gradle capability conflict check: import `io.codehunters:sdk-bom` to keep
  the `sdk-event-messaging-*` artifacts on one version.
