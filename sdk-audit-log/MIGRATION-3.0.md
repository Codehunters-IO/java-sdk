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
6. **fail-on-error.** With `fail-on-error=true` events are now written synchronously, inside the
   caller's transaction when there is one, and a failed insert fails the audited call. That is
   what the flag always promised; check that callers expect `AuditPersistenceException`.
7. **Timestamps.** On SQL Server, rows written by 2.x on hosts not running in UTC have
   `event_created_at` shifted by the host's UTC offset.
8. **AuditEventJdbcAdapter.** Code that built it directly gets a synchronous publisher; wrap it in
   `AsyncAuditEventPublisher` for the old behaviour. The constructor no longer carries
   `@Qualifier`.
