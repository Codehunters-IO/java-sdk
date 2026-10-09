# 0021 — PostgreSQL only, configuration under codehunters.* only

- Status: accepted
- Date: 2026-10-09
- Supersedes: [0004](0004-single-codehunters-property-prefix.md); the SQL Server parts of
  [0015](0015-domain-agnostic-geofencing.md) and [0018](0018-charge-attempts-only-for-started-sends.md)

## Context

The SDKs carried two kinds of weight from the product line they were extracted from:

- **Property aliases.** [ADR 0004](0004-single-codehunters-property-prefix.md) moved every key under
  `codehunters.<sdk>.*` but kept the older prefixes (`outbox.*`, `keycloak.*`, `geo.validation.*` and
  others) working through `*LegacyProperties` post-processors built on the internal `sdk-support`
  module. Every module that had aliases depended on `sdk-support`, and its docs had to explain both
  namespaces.
- **SQL Server.** The outbox had a SQL Server claim dialect next to the PostgreSQL one and a generic
  fallback; geofencing had a SQL Server stored-procedure adapter; audit and the outbox shipped SQL
  Server DDL; jpa and parameter documented SQL Server specifics. Each needed its own integration
  tests on an image that does not run natively on every development machine.

No service consumes the `io.codehunters` artifacts yet, so removing both costs no migration today and
keeps the SDK independent of the services it came from.

## Decision

- **PostgreSQL is the only supported database.** The outbox issues the PostgreSQL claim and purge
  directly; there is no dialect selection and no `claim.dialect` property. Startup fails when the
  outbox data source reports another product. Geofencing keeps the polygon and PostGIS adapters.
  Reference DDL ships for PostgreSQL only (outbox, audit, parameters).
- **All configuration lives under `codehunters.*`.** No module reads an older prefix; the
  `*LegacyProperties` classes and their deprecated metadata are gone. `sdk-web` aligns its keys with
  its artifact: `codehunters.web.errors`, `codehunters.web.device`, `codehunters.web.i18n`.
- **`sdk-support` is removed.** It only held `LegacyPropertyAliases`. The BOM stops managing it.
- **Nothing in the SDK names the previous product line** — not in code, tests, metadata or docs.

## Consequences

- Good: one namespace and one database to document, test and support; no alias post-processors at
  startup; one fewer artifact; integration tests run on images available everywhere.
- Good: a misconfigured outbox database fails at startup with the detected product name instead of
  at the first claim.
- Bad: a service on SQL Server cannot use the outbox, audit DDL or the SQL Server geofence adapter;
  it keeps the older artifacts or moves to PostgreSQL.
- Bad: services still on the older property prefixes must rename their keys before upgrading; old
  keys are silently ignored, so required keys (for example `codehunters.file-share.base-url`) fail
  startup while optional ones fall back to defaults.
- New majors: `sdk-event-messaging-*` 9.0.0, `sdk-web` 3.0.0, `sdk-audit-log` 5.0.0,
  `sdk-file-share` 4.0.0, `sdk-user-security` and `-authz` 5.0.0, `sdk-geo-validation` 2.0.0,
  `sdk-jpa` 3.0.0, `sdk-parameter` 4.0.0, `sdk-bom` 4.0.0; `sdk-commons` 3.0.0 ships without the
  aliases.

## Confirmation

- No `*LegacyProperties`, `spring.factories` alias entry or deprecated metadata remains; the build
  has no `sdk-support` project.
- `rg -i` over the repository finds no mention of the previous product line.
- The outbox auto-configuration test fails startup for a data source that reports H2.
