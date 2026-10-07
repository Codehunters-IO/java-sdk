# Changelog — sdk-audit-log

## 3.0.0

### Breaking
- Properties move to `codehunters.audit.*`; `vitxo.audit.fail-on-error` is deprecated and honoured
  until 4.0.0.
- `errorMessage` holds the exception class name instead of its message.
- The JDBC publisher bean is `auditEventPublisher` (was `auditEventJdbcAdapter`).
  `AuditEventJdbcAdapter` is synchronous, without `@Async` or `@Transactional`.
- The auto-configuration no longer applies `@EnableAsync` or `@EnableAspectJAutoProxy` to the
  application. Boot's AOP auto-configuration applies the aspect; with `spring.aop.auto=false`,
  auditing needs AspectJ proxying enabled by the application.
- The JDBC publisher needs a single `DataSource` candidate; with several and no primary it is not
  created (2.x failed startup).
- Without `fail-on-error`, the success event of a transactional call is written after the
  transaction completes, in its own transaction, instead of inside it.
- `auditTaskExecutor` and `auditJsonMapper` are not autowiring candidates.
- The SQL Server reference script moved from `db/migration/V1__create_audit_event_table.sql` to
  `db/migration/audit/sqlserver/audit_event_create.sql`; Flyway no longer runs it.

### Added
- `AsyncAuditEventPublisher` and `AuditEventRejectionListener`; the `audit.events.rejected`
  counter.
- `codehunters.audit.async.*` executor settings, validated at startup.
- `codehunters.audit.order` and `AuditAspect.Options`; the aspect implements `Ordered`.
- Transaction-aware publishing: error rows survive the rollback, success rows follow the commit,
  and `ROLLED_BACK` / `UNKNOWN` record calls whose transaction did not commit.
- `FailSafeAuditEventPublisher`, `AuditEventFailureListener` and the `audit.events.failed`
  counter. Both counters are registered at startup.
- `JsonSnapshotAuditEventPublisher`: payloads are copied to JSON on the caller's thread.
- PostgreSQL reference DDL.
- `AuditAspect(publisher, contextProvider, Clock)`; the application's `Clock` bean is used.
- `AuditEvent.withOutcome(status, success, errorMessage)`.
- Methods returning a `CompletionStage`, or a Reactor `Mono` or `Flux`, are audited when the work
  finishes, with its outcome and duration.
- `AuditPersistenceException.redacted(...)`, `failureClass()`, `sqlState()` and `vendorCode()`.
- A startup warning when one of several transaction managers is a `JtaTransactionManager`, or when
  the only one cannot suspend a transaction.
- `AuditEventPublisherPort#publishDetached`, used for events of work that completes on another
  thread.
- `CANCELLED` status for cancelled futures and Reactor subscriptions.

### Fixed
- `fail-on-error=true` never failed the audited call: the insert ran on another thread and the
  aspect swallowed the exception.
- The SDK's executor suppressed Boot's `applicationTaskExecutor`, and its mapper could replace the
  application's `JsonMapper`.
- A non-UUID correlation id or a SpEL syntax error dropped the whole event, and so did an
  expression or context provider that threw.
- The `ERROR` event of a transactional call was rolled back with it; on Postgres it failed after
  a SQL error had aborted the transaction.
- An asynchronous `SUCCESS` could be written for a transaction that then failed to commit.
- Payload objects were serialized later on the worker thread, racing with the caller and failing
  on closed persistence sessions; a failed serialization stored `toString()`.
- A failed insert logged the database message, which can contain the row's personal data. In
  fail-on-error mode the `AuditPersistenceException` carried that message in its cause; it is now
  redacted.
- Asynchronous return types were recorded as `SUCCESS` when the work started.
- Completion events joined, or took the outcome of, an unrelated transaction on the completing
  thread, and read the context provider and MDC of that thread.
- With fail-on-error, publisher failures other than `AuditPersistenceException` were swallowed
  and logged with their message.
- Completion events were written on Netty event loops; a declared `ConnectableFlux` return type
  failed with `ClassCastException`; an `Error` in the completion callback failed the caller's
  `Mono`.
- An `Error` from the publisher replaced the method's own exception.
- `#result` resolved to a parameter named `result` when the method threw.
- On SQL Server, `event_created_at` was shifted by the JVM's UTC offset.
- Durations used the wall clock; timestamps used the system default zone.
- Every event re-parsed its SpEL expressions.
- The executor did not carry the MDC to its workers and did not wait for queued events on
  shutdown.
