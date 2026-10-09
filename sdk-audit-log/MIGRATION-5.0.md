# Migrating sdk-audit-log 4.x to 5.0

5.0.0 supports PostgreSQL only and reads only `codehunters.audit.*`. Packages, class names and the
`audit_event` columns are those of 4.0.0.

## Steps

1. **Dependency.** Bump `sdk-audit-log` to 5.0.0, or the `sdk-bom` that manages it. With `sdk-web`
   on the classpath, use sdk-web 3.0.0.
2. **Database.** PostgreSQL is the only supported database. The jar ships one reference DDL,
   `db/migration/audit/postgresql/audit_event_create.sql`; copy it into your own migrations. Other
   databases are neither shipped a script nor tested: a service on another database moves the
   `audit_event` table to PostgreSQL, or declares its own `AuditEventPublisherPort`.
3. **Properties.** Only `codehunters.audit.*` binds; the 2.x alias of `fail-on-error` is gone. Set
   `codehunters.audit.fail-on-error` (`CODEHUNTERS_AUDIT_FAILONERROR` as an environment variable)
   if you relied on the old key: without it the default, `false`, applies silently.
4. **Client IP.** The default `AuditContextProvider` takes the IP from `sdk-web`'s device part,
   which is configured under `codehunters.web.device.*` since sdk-web 3.0.0. Rename
   `codehunters.device.trusted-proxies` and `codehunters.device.forwarding-header` to
   `codehunters.web.device.trusted-proxies` and `codehunters.web.device.forwarding-header`, or the
   recorded IP becomes the proxy's address. See [sdk-web's MIGRATION-3.0.md](../sdk-web/MIGRATION-3.0.md).

## New in 5.0

What was prepared as 4.1.0 ships here (see the [CHANGELOG](CHANGELOG.md)):

- Startup checks log an `ERROR` when `@Auditable` methods have no publisher or no `aspectjweaver`,
  or carry an expression that cannot be parsed; `codehunters.audit.startup-check.fail-fast=true`
  makes them fail startup.
- `codehunters.audit.enabled` (default `true`) turns the SDK off.
- Servlet calls with no actor from the annotation or the provider record the authenticated user of
  the request thread. Reports that read a `null` actor as "unknown user" see it filled.
