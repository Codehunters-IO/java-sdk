# Changelog — sdk-audit-log

## 3.0.0

### Breaking
- Properties move to `codehunters.audit.*`; `vitxo.audit.fail-on-error` is deprecated and honoured
  until 4.0.0.
- `errorMessage` holds the exception class name instead of its message.
- The JDBC publisher bean is `auditEventPublisher` (was `auditEventJdbcAdapter`).
  `AuditEventJdbcAdapter` is synchronous, without `@Async` or `@Transactional`.
- The auto-configuration no longer applies `@EnableAsync` to the application.
- `auditTaskExecutor` and `auditJsonMapper` are not autowiring candidates.
- The SQL Server reference script moved from `db/migration/V1__create_audit_event_table.sql` to
  `db/migration/audit/sqlserver/audit_event_create.sql`; Flyway no longer runs it.

### Added
- `AsyncAuditEventPublisher` and `AuditEventRejectionListener`; the `audit.events.rejected`
  counter.
- `codehunters.audit.async.*` executor settings, validated at startup.
- PostgreSQL reference DDL.
- `AuditAspect(publisher, contextProvider, Clock)`; the application's `Clock` bean is used.

### Fixed
- `fail-on-error=true` never failed the audited call: the insert ran on another thread and the
  aspect swallowed the exception.
- The SDK's executor suppressed Boot's `applicationTaskExecutor`, and its mapper could replace the
  application's `JsonMapper`.
- A non-UUID correlation id or a SpEL syntax error dropped the whole event.
- On SQL Server, `event_created_at` was shifted by the JVM's UTC offset.
- Durations used the wall clock; timestamps used the system default zone.
- Every event re-parsed its SpEL expressions.
- The executor did not carry the MDC to its workers and did not wait for queued events on
  shutdown.
