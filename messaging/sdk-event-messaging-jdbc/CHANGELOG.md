# Changelog — sdk-event-messaging-jdbc

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
- `outbox_failed_messages{origin}` gauge.
- PostgreSQL reference DDL.
- `codehunters.outbox.scheduling.enabled` (default `true`): `false` leaves out the dispatch and purge
  schedulers and `@EnableScheduling` / `@EnableSchedulerLock`, for applications that trigger
  dispatch themselves or in another process.

### Fixed
- Reclaiming a stale `IN_FLIGHT` row did not count as an attempt, so a message that kept crashing
  its dispatcher looped forever; reclaims now increment `attempts` in all three dialects.
- `outboxJsonMapper` took part in by-type `JsonMapper` injection and could clash with the
  application's mapper; it is now reachable only by qualifier.
