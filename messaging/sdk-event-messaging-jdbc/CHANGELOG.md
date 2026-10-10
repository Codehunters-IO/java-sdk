# Changelog — sdk-event-messaging-jdbc

## 9.0.0

Migration guide: [MIGRATION-9.0.md](../MIGRATION-9.0.md).

### Removed
- Every database but PostgreSQL: the non-PostgreSQL claim dialect, its reference DDL (the top-level
  `db/migration/outbox/outbox_message_create.sql` and its upgrade scripts) and its integration
  tests.
- The dialect selection: `OutboxDialect`, `OutboxDialectResolver`, `PostgresOutboxDialect`,
  `GenericOutboxDialect`, the `OutboxDialect` bean and `codehunters.outbox.claim.dialect`.
- `@EnableScheduling` and `@EnableSchedulerLock` (with its `60s` default lock-at-most): the module
  no longer turns on application-wide scheduling, and `OutboxScheduler` / `OutboxPurgeScheduler`
  carry no `@Scheduled` / `@SchedulerLock`.
- The `outboxRepositoryPort` bean (typed `OutboxRepositoryPort`), replaced by `outboxJdbcAdapter`.
- Lombok from the build.

### Added
- A startup check: the outbox data source must report PostgreSQL as its product, or startup fails
  naming the detected product. An unreachable database is skipped with a warning.
- Column `traceparent VARCHAR(55)` (nullable) in the reference DDL, and the idempotent upgrade
  script `postgresql/outbox_message_add_traceparent.sql` for tables created by 6.x to 8.x.
  `save` stores `OutboxMessage.traceparent` and the claim returns it.
- Private scheduling: bean `outboxTaskScheduler` (`ThreadPoolTaskScheduler`, 3 threads `outbox-*`,
  not a default candidate) and `outboxSchedulingLifecycle` (`OutboxSchedulingLifecycle`), which
  schedules every `OutboxScheduledTask` bean (`outboxDispatchTask`, `outboxPurgeTask`) and wraps
  each run in ShedLock through the `LockProvider` bean. New records `OutboxScheduledTask`,
  `OutboxTaskSchedule` and `OutboxTaskLock`. A replacement `outboxTaskScheduler` must also be an
  `Executor` while dispatch on commit is on.
- Dispatch on commit: `OutboxAdapterSettings(origin, clock, trigger)`; `save` asks the trigger for
  a dispatch after the transaction commits (once per transaction), or right away outside one. Bean
  `outboxDispatchTrigger` (a `CoalescingDispatchTrigger` on `outboxTaskScheduler`), registered while
  `scheduling.enabled` and `scheduling.dispatch-on-commit` are on. `OutboxScheduler(port, trigger)`
  requests another dispatch when a poll's claim comes back full.
- Per-key batched claim: up to `claim.max-per-key` consecutive rows of one key per claim; a row
  skipped because another claimer holds it still blocks its successors.
- `requeue(ids)` and `requeueFailed(key)`: `FAILED` rows of the origin back to `PENDING`, due now,
  with no attempts, error, claim or send mark; a dispatch is requested after the commit. `requeue`
  binds the ids into one `IN (...)` list, so chunk very large lists.
- `OutboxMetricsAutoConfiguration`, which now registers `OutboxFailedGauge`.

### Changed
- `OutboxJdbcAdapter` implements `OutboxWriter` and `OutboxDispatchStore`. The auto-configured
  bean is `outboxJdbcAdapter`, typed `OutboxJdbcAdapter`, and backs off when the application
  defines either port.
- `OutboxScheduler` and `OutboxPurgeScheduler` are `Runnable`s (`run()` replaces
  `processOutboxMessages()` and `purgePublished()`); they, `OutboxFailedGauge` and the purge take an
  `OutboxDispatchStore` and an `Instant` cutoff. `outboxPurgeScheduler` moved to
  `OutboxDispatchSchedulerAutoConfiguration`.
- Every SDK bean injects the `outboxClock` by qualifier.
- `outboxJsonMapper` is built from the application's `JsonMapper.Builder` when Boot's Jackson
  auto-configuration provides one, with indented output and date timestamps forced off; a plain
  `JsonMapper` otherwise.
- `OutboxJdbcAdapter` issues the PostgreSQL claim and purge itself. `OutboxAdapterSettings` is
  `(origin, clock, trigger)` and `defaults(origin)` no longer reads the database metadata and leaves
  dispatch to the poll.
- The adapter tests run on PostgreSQL Testcontainers instead of H2.

### Fixed
- The task and purge lock names no longer depend on a property default that was never resolved;
  unset names fall back to `outbox-<application>` / `outbox-purge-<application>`.

### Behaviour changes
- Saving and claiming reference `traceparent`: apply the upgrade script **before** deploying 9.0,
  or every save and claim fails.
- `save` stores `NULL` for a `traceparent` that is not a valid `W3cTraceParent` (for example a
  hand-built row with a value longer than the 55-character column), instead of failing the
  business transaction.
- Application `@Scheduled` / `@SchedulerLock` methods that ran only because this module enabled
  scheduling stop running until the application adds `@EnableScheduling` /
  `@EnableSchedulerLock` itself.
- Messages are dispatched right after their transaction commits instead of on the next poll.
  Measured on PostgreSQL 17 with a 4-connection pool and an in-memory publisher: about 2.5 ms from
  commit to publish, and a 2000-row backlog at about 1100 msg/s (`max-per-key=1`) or 1490 msg/s
  (`max-per-key=10`), against at most about 10 msg/s from 8.x polling. With a real broker the rate
  is about one message per acknowledgement latency per instance.
- Commit-triggered runs do not take the ShedLock dispatch lock; concurrent claimers receive
  disjoint rows and updates stay fenced.
- `task.lock-at-least` defaults to `0` (was `10000` ms).
- A global Jackson customisation (naming strategy, modules) now applies to the stored message,
  envelope included.

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
  `postgresql/outbox_message_add_send_started_at.sql` for tables created by 6.x.

### Fixed
- Reclaiming a stale `IN_FLIGHT` row charges an attempt only when its send had started
  ([ADR 0018](../../docs/adr/0018-charge-attempts-only-for-started-sends.md)); rows the previous
  dispatcher never reached keep their attempts.

### Added
- `OutboxJdbcAdapter.markSendStarted`, fenced by the claim marker. Updates that end a claim clear
  the mark. It warns once when it runs inside a caller transaction: dispatch must run outside one.

## 6.0.0

First release; the JDBC store, schedulers and DDL of `sdk-event-messaging` 5.1.5. Migration guide:
[MIGRATION-6.0.md](../MIGRATION-6.0.md).

### Breaking
- Replaces `sdk-event-messaging` together with `-rabbit` or `-kafka`.
- Scheduler and lock keys move to `codehunters.outbox.task.*` and `codehunters.outbox.purge.*`
  (old `outbox.*` keys honoured until 7.0).
- The claim orders by a `seq` column and blocks a row while an earlier row with the same
  `(origin, topic, message_key)` is `PENDING`, `IN_FLIGHT` or `FAILED`. Tables created before 6.0
  need the `seq` column **before** the deploy.
- `task.lock-at-most` defaults to `300000` (was `60000`): the 5.x lock was shorter than a dispatch
  run (`claim.ttl-ms / 2` + one transport timeout, 155 s by default), so ShedLock could free it
  while a run was still publishing. A shorter configured value now fails startup.

### Added
- Per-database claim dialects selected by `codehunters.outbox.claim.dialect` (default `auto`),
  PostgreSQL using `SKIP LOCKED` (removed in 9.0, which supports PostgreSQL only).
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
