# Migrating sdk-event-messaging to 7.0

7.0 charges a reclaim attempt only to rows whose send had started
([ADR 0018](../docs/adr/0018-charge-attempts-only-for-started-sends.md)). The four artifacts
(`-core`, `-jdbc`, `-rabbit`, `-kafka`) move to 7.0.0 together; import `io.codehunters:sdk-bom:1.3.0`
to keep them aligned. Java packages and `codehunters.outbox.*` properties do not change.

**Coming from 5.x?** Apply [MIGRATION-6.0.md](MIGRATION-6.0.md) first (coordinates, properties,
broker settings, the `seq` column), then this guide. You can go straight from 5.x to 7.0.0.

## Checklist

1. Add the `send_started_at` column **before** deploying 7.0 (see [DDL](#ddl)).
2. Bump the dependency (or the BOM) to 7.0.0.
3. If you implement `OutboxRepositoryPort` yourself, add `markSendStarted` (see
   [Custom repositories](#custom-repositories)).
4. Optional: rename the old `outbox.*` keys. They are honoured until 8.0 (they were due to go in 7.0).

## What changes

Before publishing each claimed row, the dispatcher writes `send_started_at` on it, fenced by its
claim marker. When a dispatcher crashes or stalls past `claim.ttl-ms`, the next claim takes its rows
over and:

| Row of the abandoned batch | 6.x | 7.0 |
|---|---|---|
| Send had started (`send_started_at` set) | `attempts + 1` | `attempts + 1` |
| Never reached by the dead dispatcher | `attempts + 1` | `attempts` unchanged |

A reclaimed row that reaches `max-attempts` is still marked `FAILED` with
`max-attempts-exceeded` without being published, as in 6.x. A row that crashes the dispatcher while
being sent is still charged on every reclaim, so a poison message still ends up `FAILED`.

Every claim clears `send_started_at`, and so does every update that ends the claim (published,
retry scheduled, failed, released). When the mark is refused because another dispatcher took the
claim over, the row is neither published nor updated, which closes a window where 6.x could send a
row another instance already owned.

**Cost:** one extra `UPDATE` by primary key per published row, in its own statement, before the
broker call.

## DDL

The SDK never runs DDL. Copy the scripts from the `-jdbc` jar (`db/migration/outbox/**`) into your
own changelog. The column is nullable and 6.x ignores it, so it can be added while 6.x is running.

| Database | Action |
|---|---|
| SQL Server, table created by 6.x | Apply `sqlserver/outbox_message_add_send_started_at.sql` |
| SQL Server, table created before 6.0 | `sqlserver/outbox_message_add_seq.sql`, then `sqlserver/outbox_message_add_send_started_at.sql` |
| PostgreSQL, table created by 6.x | Apply `postgresql/outbox_message_add_send_started_at.sql` |
| New install | `postgresql/outbox_message_create.sql` or `outbox_message_create.sql` (SQL Server) |

Without the column every claim fails: the claim SQL reads and clears `send_started_at`.

## Custom repositories

`OutboxRepositoryPort` gains:

```java
boolean markSendStarted(OutboxMessage<String> outboxMessage);
```

Write the send mark only while the caller still holds the claim (`claimed_by` equals the message's
claim marker and the row is `IN_FLIGHT`), and return whether a row was updated. Your claim should
charge an attempt on reclaim only when the mark is set, and clear it. A decorator around the SDK's
adapter can delegate. `OutboxMessage` has a new `sendStartedAt` property.

## Known limits

- Maven does not apply the Gradle capability conflict check: import `io.codehunters:sdk-bom` to keep
  the `sdk-event-messaging-*` artifacts on one version.
