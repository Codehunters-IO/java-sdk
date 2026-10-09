---
status: accepted (SQL Server adapter superseded by 0021)
date: 2026-10-07
decision-makers: [andres]
supersedes: 0003
---

# 0015. Keep sdk-geo-validation as domain-agnostic geofencing

## Context and Problem Statement

[ADR 0003](0003-retire-geo-validation-from-the-sdk.md) moved `sdk-geo-validation` out of the SDK,
because it exposed a port and a SQL Server procedure specific to one service and
registered itself in any application with a `DataSource`. No team took the code over, and
"is this point inside the zones that apply here?" is a question other services ask too: delivery
areas, store coverage, regional campaigns. Should the SDK keep geofencing, and in what shape?

## Decision Drivers

- No domain concept of any one service in the SDK.
- No bean registered unless the service asks for it.
- Usable without a database, and without Spring.
- The existing deployment can keep its stored procedure.

## Considered Options

- Option A — Hand the module to the service that uses it and delete it (ADR 0003).
- Option B — Keep it, generalised: an opaque scope reference, a neutral decision, a pure-JDK polygon
  adapter and a SQL Server adapter whose procedure and parameter names are configured.

## Decision Outcome

Chosen option: **Option B**, because the generalisation is small (one port, two adapters), the
second use case now exists, and the existing service can migrate by configuration instead of copying
code.

- The port is `GeoFenceValidator.validate(ZoneRef, GeoPoint[, Coverage])`. A `ZoneRef` is opaque:
  only the zone source or the procedure interprets it.
- `GeoDecision` is allowed (with a `MatchedZone` whose type is free text) or denied with
  `OUTSIDE_ZONE` or `ZONE_NOT_FOUND`.
- `PolygonGeoFence` runs point-in-polygon on zones from the service's `GeoZoneSource`.
- `SqlServerGeoFence` calls the procedure named in `codehunters.geo.sql-server.procedure`; names are
  validated as identifiers because they are written into the statement.
- Nothing is registered until `codehunters.geo.adapter` selects `polygon` or `sql-server`; an
  application `GeoFenceValidator` always wins.

### Consequences

- **Good** — no service-specific code in the SDK; services without SQL Server can use the polygon
  adapter; the existing service keeps its procedure through configuration.
- **Bad** — 1.0.0 breaks every 0.2.0 caller (port, decision, configuration); see the
  `sdk-geo-validation` CHANGELOG.
- **Neutral** — the polygon math is planar on degrees, fine for city- and region-sized zones but not
  for continent-sized ones; zones crossing the antimeridian must be split.

### Confirmation

- An ArchUnit test keeps the model and the polygon adapter free of frameworks and the model free of
  adapters.
- A SQL Server integration test calls a sample procedure with custom parameter names.
- `sdk-bom` 1.1.0 constrains `sdk-geo-validation` 1.0.0.

## More Information

- Migration plan: `docs/plans/2026-10-05-sdk-migration.md`, phase 18.

## Later changes

[ADR 0021](0021-postgresql-only-and-codehunters-properties.md) removes the SQL Server adapter and
the `geo.validation.*` alias in `sdk-geo-validation` 2.0.0; the polygon and PostGIS adapters remain.
