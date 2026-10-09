# Changelog — sdk-event-messaging-jdbc

## 8.0.0

Migration guide: [MIGRATION-8.0.md](../MIGRATION-8.0.md).

### Breaking
- Packages reorganised by capability: the store and dialects move to
  `io.codehunters.messaging.jdbc`, the schedulers to `io.codehunters.messaging.jdbc.scheduler`,
  `OutboxFailedGauge` to `io.codehunters.messaging.jdbc.metrics` and the auto-configurations to
  `io.codehunters.messaging.jdbc.config`. Only imports change; the `db/migration/outbox/**`
  scripts keep their paths.

## 7.0.0

Migration guide: [MIGRATION-7.0.md](../MIGRATION-7.0.md).

### Breaking
- The claim reads and clears a new `send_started_at` column. Add it **before** the deploy:
  `sqlserver/outbox_message_add_send_started_at.sql` or
  `postgresql/outbox_message_add_send_started_at.sql` for tables created by 6.x.

### Fixed
- Reclaiming a stale `IN_FLIGHT` row charges an attempt only when its send had started
  ([ADR 0018](../../docs/adr/0018-charge-attempts-only-for-started-sends.md)); rows the previous
  dispatcher never reached keep their attempts.

### Added
- `OutboxJdbcAdapter.markSendStarted`, fenced by the claim marker. Updates that end a claim clear
  the mark. It warns once when it runs inside a caller transaction: dispatch must run outside one.
- Integration tests against the SQL Server reference DDL and the 7.0 upgrade scripts (SQL Server
  2022 on x86-64, azure-sql-edge on ARM).

## 6.0.0

First release; the JDBC store, schedulers and DDL of `sdk-event-messaging` 5.1.5. Migration guide:
[MIGRATION-6.0.md](../MIGRATION-6.0.md).

### Breaking
- Replaces `sdk-event-messaging` together with `-rabbit` or `-kafka`.
- Scheduler and lock keys move to `codehunters.outbox.task.*` and `codehunters.outbox.purge.*`
  (old `outbox.*` keys honoured until 7.0).
- The claim orders by a `seq` column and blocks a row while an earlier row with the same
  `(origin, topic, message_key)` is `PENDING`, `IN_FLIGHT` or `FAILED`. SQL Server tables created
  before 6.0 need `sqlserver/outbox_message_add_seq.sql` **before** the deploy.
- `task.lock-at-most` defaults to `300000` (was `60000`): the 5.x lock was shorter than a dispatch
  run (`claim.ttl-ms / 2` + one transport timeout, 155 s by default), so ShedLock could free it
  while a run was still publishing. A shorter configured value now fails startup.
- `claim.dialect=auto` fails startup on databases other than PostgreSQL, SQL Server and H2 instead
  of falling back to a non-atomic claim.
- MySQL and MariaDB, unsupported, now fail startup with `auto` or `generic` instead of failing every
  dispatch with error 1093 or a `FETCH FIRST` syntax error.

### Added
- Claim dialects `postgresql` (`SKIP LOCKED`), `sqlserver` (`UPDLOCK, READPAST`) and `generic`,
  selected by `codehunters.outbox.claim.dialect` (default `auto`).
- Claim fencing: an update only applies while this instance still holds the claim.
- Purge of `PUBLISHED` rows older than `purge.retention` (default `7d`), per origin, in batches,
  ShedLock-protected.
- `outbox_failed_messages{origin}` gauge, counting `FAILED` rows at most once per 30 s.
- PostgreSQL reference DDL.
- `codehunters.outbox.scheduling.enabled` (default `true`): `false` leaves out the dispatch and purge
  schedulers and `@EnableScheduling` / `@EnableSchedulerLock`, for applications that trigger
  dispatch themselves or in another process.

### Fixed
- Reclaiming a stale `IN_FLIGHT` row did not count as an attempt, so a message that kept crashing
  its dispatcher looped forever; reclaims now increment `attempts` in all three dialects.
- `outboxJsonMapper` took part in by-type `JsonMapper` injection and could clash with the
  application's mapper; it is now reachable only by qualifier.
