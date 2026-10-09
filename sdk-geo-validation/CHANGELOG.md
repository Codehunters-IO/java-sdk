# Changelog — sdk-geo-validation

## 2.0.0

PostgreSQL only. See [MIGRATION-2.0.md](MIGRATION-2.0.md).

### Removed

- The stored-procedure adapter: `codehunters.geo.adapter=sql-server`, `GeoAdapter.SQL_SERVER`, the
  `io.codehunters.geo.sqlserver` package (`SqlServerGeoFence`, `SqlServerProcedure`),
  `GeoProperties.sqlServer()` and the `codehunters.geo.sql-server.*` properties. SQL Server is no
  longer supported; use `postgis` or `polygon`.
- The `geo.validation.enabled` alias. Only `codehunters.geo.enabled` is read.
- `GeoLegacyProperties`, its `spring.factories` entry and the deprecated configuration metadata.
- The dependency on `sdk-support`.

## 1.1.0

### Added

- `PostgisGeoFence` and `codehunters.geo.adapter=postgis`: zones read from an application-owned
  table or view (`codehunters.geo.postgis.table`, `.columns.*`), coverage tested on the geography
  with `ST_Covers` ([ADR 0019](../docs/adr/0019-postgis-geofence-adapter.md)). Edges are
  great-circle arcs, zones may cross the antimeridian, a border point is inside. One round trip
  tells `ZONE_NOT_FOUND` from `OUTSIDE_ZONE`; the lowest zone id in the column's own order wins
  when several cover the point, and the coverage test uses the spatial index (geography GIST, or
  GIST on `geom::geography`).
- `codehunters.geo.postgis.query-timeout` (default `2s`), on a template of its own.

## 1.0.0

Domain-agnostic geofencing ([ADR 0015](../docs/adr/0015-domain-agnostic-geofencing.md), which
supersedes ADR 0003).

### Breaking

- `RaffleGeoValidationPort.canPurchase(raffleId, point, coverageType)` is replaced by
  `GeoFenceValidator.validate(ZoneRef, GeoPoint[, Coverage])`.
- `GeoValidationDecision` is replaced by `GeoDecision` (`allowed`, `Optional<MatchedZone> zone`,
  `Optional<DenialReason> denialReason`); `MatchedZone` keeps the zone type as text.
- `CoverageType` (`NATIONAL`, `DEPARTMENTAL`, `LOCAL`) is replaced by `Coverage` (`UNRESTRICTED`,
  `RESTRICTED`). `MatchedZoneType` is removed.
- `DenialReason.OUTSIDE_ALLOWED_ZONE` becomes `OUTSIDE_ZONE`; `ZONE_NOT_FOUND` is new.
- The stored-procedure adapter no longer calls a fixed procedure and is no longer registered
  because a `DataSource` exists: it is selected with `codehunters.geo.adapter`.
- Properties move under `codehunters.geo.*`.

### Added

- `PolygonGeoFence` over a `GeoZoneSource`: point-in-polygon with holes, borders inside,
  antimeridian edges rejected. Pure JDK.
- A stored-procedure adapter with configurable procedure and parameter names and a query timeout
  (removed in 2.0.0).
- Polygon rings keep a bounding box, so distant points are rejected without walking the edges.
- Polygon rings that enclose no area (collinear vertices) are rejected.
- `GeoPoint.of(latitude, longitude)` and `ZoneRef`.

### Fixed

- An unknown zone type returned by the procedure no longer throws (`MatchedZoneType.valueOf`); it is
  reported as text.
- With several `DataSource` beans and no primary, the stored-procedure adapter fails at startup
  with a clear message instead of an ambiguous-bean error.
- `Latitude` and `Longitude` reject `NaN` and infinities, without repeating the coordinate.

## 0.2.0

Purchase validation over a fixed stored procedure.
