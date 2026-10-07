# Migrating sdk-audit-log 2.x to 3.0

1. **Properties.** Rename `vitxo.audit.fail-on-error` to `codehunters.audit.fail-on-error`. The old
   key works until 4.0.0 and logs a warning at startup.
2. **Flyway.** 2.x shipped `db/migration/V1__create_audit_event_table.sql`, which Flyway ran in any
   service that scans its default location. 3.0 no longer ships it. If your schema history
   contains that migration, either copy the file into your own `db/migration` with the same name,
   or set `spring.flyway.ignore-migration-patterns=*:missing`. New services copy
   `db/migration/audit/<database>/audit_event_create.sql` into their migrations.
3. **Error messages.** `errorMessage` is now the exception class name. Queries or dashboards that
   matched on message text must match on the class name.
4. **`@Async`.** The SDK no longer enables `@Async` for the whole application. If your own
   `@Async` methods ran asynchronously only because of the SDK, add `@EnableAsync` to your
   configuration.
5. **Beans.** The publisher bean is `auditEventPublisher`. `auditTaskExecutor` and
   `auditJsonMapper` need `@Qualifier` to be injected, and no longer replace Boot's
   `applicationTaskExecutor` or your `JsonMapper`. A custom `auditTaskExecutor` bean still
   replaces the SDK's.
6. **fail-on-error.** With `fail-on-error=true` events are now written synchronously and a failed
   build or insert fails the audited call. That is what the flag always promised; check that
   callers expect `AuditPersistenceException`. Inside a transaction, the success row commits or
   rolls back with it, while the error row is written in a transaction of its own and survives the
   rollback.
7. **Transactions without fail-on-error.** The success row of a transactional call is written after
   the transaction completes. A call whose transaction rolls back is recorded with
   `success=false` and `errorMessage=ROLLED_BACK`, or `UNKNOWN` with `success` null when the commit
   itself failed. Dashboards that count `success=false` as exceptions should also look at these
   two values.
8. **Several data sources.** The JDBC publisher is created only when there is a single
   `DataSource` candidate. Mark one `@Primary`, or declare your own `AuditEventPublisherPort`.
9. **Aspect order.** The aspect now has an explicit order, `codehunters.audit.order`, lowest
   precedence by default, so it runs inside `@Transactional`. If you set a lower order for
   `@EnableTransactionManagement`, keep the audit order above it.
10. **Timestamps.** On SQL Server, rows written by 2.x on hosts not running in UTC have
    `event_created_at` shifted by the host's UTC offset.
11. **AuditEventJdbcAdapter.** Code that built it directly gets a synchronous publisher; wrap it in
    `AsyncAuditEventPublisher` for the old behaviour. The constructor no longer carries
    `@Qualifier`. With `fail-on-error=false` it now stores `null`, not `toString()`, for a map it
    cannot serialize.
12. **Asynchronous return types.** Methods returning a `CompletionStage`, `Mono` or `Flux` are now
    recorded when the work finishes, with its real outcome: a failed future is `ERROR`, not
    `SUCCESS`. Each subscription to a `Mono` or `Flux` records one event; a cancelled one records
    none.
13. **`AuditPersistenceException`.** Its cause no longer carries the database message. Code that
    inspected `getCause()` for the SQL error should read `sqlState()`, `vendorCode()` and
    `failureClass()` instead.
14. **JTA.** If the application has several transaction managers and one is a
    `JtaTransactionManager`, mark that one as primary; the SDK warns at startup otherwise.
