# Changelog — sdk-audit-log

## 5.1.0

### Added
- `codehunters.audit.sink` selects where events go: `jdbc` (default, the 5.0 behavior: the
  `audit_event` table) or `outbox`. Any other value fails startup.
- `OutboxAuditEventPublisher`: with `sink=outbox`, every event is written through
  `sdk-event-messaging`'s `OutboxWriter`, synchronously in the caller's transaction, so the audit
  trail records what committed. The payload is the sanitized `AuditEvent` (no IP address, user
  agent or response summary; error messages only as class names) in the `EventMessage` envelope,
  with the action as event name (`FALLBACK_TYPE` = `AUDIT` when blank), `schemaVersion` 1.0.0 and
  the entity id as message key. Completion events of asynchronous calls are written outside the
  bound transaction. See [Publishing through the outbox](README.md#publishing-through-the-outbox)
  and [ADR 0029](../docs/adr/0029-audit-events-through-the-outbox.md).
- `codehunters.audit.outbox.topic`: destination of audit events, required with `sink=outbox`.
- Sink validation at startup: `sink=outbox` without `sdk-event-messaging-jdbc` fails naming the
  artifact, without an `OutboxWriter` it fails naming `codehunters.outbox.enabled`, and a blank
  topic fails naming the property. With `sink=outbox` the JDBC publisher, executor and mapper are
  not created.

## 5.0.0

4.1.0 was never released; its changes ship in 5.0.0.

### Breaking
- PostgreSQL is the only supported database: the reference DDL for any other database is
  removed, with its integration tests and Testcontainers driver. See [MIGRATION-5.0.md](MIGRATION-5.0.md).
- The 2.x `fail-on-error` property alias is removed, with its `EnvironmentPostProcessor`
  (`AuditLegacyProperties`) and its deprecated configuration metadata: only `codehunters.audit.*`
  binds.
- With `sdk-web` on the classpath, use sdk-web 3.0.0: the client IP of audit events follows
  `codehunters.web.device.*`.

- `@Auditable` expressions run in a read-only SpEL sandbox (`SimpleEvaluationContext`) instead of
  a full `StandardEvaluationContext`: property reads, indexes and operators only; no method calls,
  `T(...)`, constructors, bean references or assignments. Such expressions now record `null` and
  are reported at startup. Rewrite `#order.getId()` as `#order.id` and `#map.get('k')` as
  `#map['k']`; see [MIGRATION-5.0.md](MIGRATION-5.0.md).

### Removed
- The dependency on `sdk-support`.

### Changed
- `auditJsonMapper` is built from Spring Boot's `JsonMapper.Builder` when the Jackson
  auto-configuration provides one, so `spring.jackson.*`, modules and customizers apply to audit
  payloads; Jackson's defaults otherwise.

### Added
- Startup checks, run once every singleton exists. When a bean declares `@Auditable` methods but
  no `AuditEventPublisherPort` exists (no `DataSource`, several with none `@Primary`, or Spring
  JDBC, Spring transactions or Jackson missing, or the JDBC auto-configuration excluded), the
  reason is logged at `ERROR`; until now every audited call went unrecorded without a word. A
  missing `aspectjweaver` is reported the same way. Every `@Auditable` expression is parsed at
  startup; one that cannot be parsed, uses what the sandbox refuses, or reads a `#variable` that is
  neither a parameter of the method nor `#result`, `#p0`/`#a0`/`#arg0`, `#root` or `#this`, is
  logged at `ERROR` naming `Class#method(Params)` and the attribute, never evaluated values. When
  the class was compiled without `-parameters`, a named variable is reported with a hint to compile
  with `-parameters` or use `#p0`/`#a0`. Publishers and data sources of parent contexts count, data
  sources registered as plain singletons are handled, and annotations declared only on interface
  methods, which the aspect does not apply to, are not reported.
- `codehunters.audit.startup-check.fail-fast` (default `false`) makes those problems fail startup
  instead of logging. `fail-on-error` does not, so a minor upgrade never stops an application.
- `codehunters.audit.enabled` (default `true`) turns the SDK off, as in the other modules.
- Servlet applications get an actor by default: when neither the annotation nor the
  `AuditContextProvider` names one and Spring Security is on the classpath, the authenticated,
  non-anonymous user of the calling thread's `SecurityContextHolder` is recorded, only while a
  request is bound to the thread, so pooled threads with an inherited or leaked context record no
  actor. Spring Security and Spring Web stay optional.

### Behaviour changes
- New log lines: `ERROR` at startup for the problems above; `WARN` the first time an expression
  of a method and attribute fails to evaluate (later failures stay at `DEBUG`). The warning names
  the exception class or SpEL message code, never its message or the values. The field is still
  recorded as `null`. Overloads warn separately. A `#result` expression that fails because the
  method threw is expected and not warned about; prefer `#result?.id`.
- The `actor_id` of servlet calls that recorded no actor is now the authenticated user's name;
  reports or queries that read a `null` actor as "unknown user" see it filled.

### Known limits
- Expressions run in a read-only `SimpleEvaluationContext` sandbox (see Breaking): property reads,
  indexes and operators only. The 4.1.0 note that they ran in a full `StandardEvaluationContext`
  no longer applies.

## 4.0.0

### Breaking
- `io.codehunters.audit` keeps only what services use: `Auditable`, `AuditEvent`,
  `AuditEventPublisherPort`, `AuditContextProvider`, `NoOpAuditContextProvider`, the failure and
  rejection listeners and `AuditPersistenceException`. Auto-configurations and properties move to
  `audit.config`, the aspect to `audit.internal.aspect`, `AsyncAuditEventPublisher` and
  `FailSafeAuditEventPublisher` to `audit.persistence`. Only imports change; see
  [MIGRATION-4.0.md](MIGRATION-4.0.md).
- Internals move too (`audit.internal.aspect`, `audit.internal.reactive`, `audit.internal.device`); the few made public for the
  auto-configuration are not API.
- The device context provider names sdk-web 2.0's `io.codehunters.web.device` classes.

## 3.1.0

### Added
- With Spring Security on the classpath, a `Mono` or `Flux` event whose annotation and
  `AuditContextProvider` name no actor records the authenticated, non-anonymous user of the
  subscriber's Reactor context (`ReactiveSecurityContextHolder`), so WebFlux calls get an actor.
  Each subscription reads its own context. A context that cannot be read leaves the actor empty.
  Spring Security stays optional.

### Verified
- One event per subscription when a caller's `timeout(...)` or `take(n)` races the work's own
  completion.
- With a `JtaTransactionManager` that has a JTA `TransactionManager`, the isolated write suspends
  the caller's global transaction and survives its rollback.

## 3.0.0

### Breaking
- Properties move to `codehunters.audit.*`.
- `errorMessage` holds the exception class name instead of its message.
- The JDBC publisher bean is `auditEventPublisher` (was `auditEventJdbcAdapter`).
  `AuditEventJdbcAdapter` is synchronous, without `@Async` or `@Transactional`.
- The auto-configuration no longer applies `@EnableAsync` or `@EnableAspectJAutoProxy` to the
  application. Boot's AOP auto-configuration applies the aspect; with `spring.aop.auto=false`,
  auditing needs AspectJ proxying enabled by the application. A startup check warns when no
  AspectJ auto-proxy creator is registered, and fails startup when `fail-on-error=true`.
- The JDBC publisher needs a single `DataSource` candidate; with several and no primary it is not
  created (2.x failed startup).
- Without `fail-on-error`, the success event of a transactional call is written after the
  transaction completes, in its own transaction, instead of inside it.
- `auditTaskExecutor` and `auditJsonMapper` are not autowiring candidates.
- `db/migration/V1__create_audit_event_table.sql` is no longer shipped; Flyway no longer runs a
  reference script.

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
- With `sdk-device` on the classpath of a servlet application, the default `AuditContextProvider`
  reports the IP (resolved behind trusted proxies) and `User-Agent` of the current request.

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
- Durations used the wall clock; timestamps used the system default zone.
- Every event re-parsed its SpEL expressions.
- The executor did not carry the MDC to its workers and did not wait for queued events on
  shutdown.
