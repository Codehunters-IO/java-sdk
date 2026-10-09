# Changelog — sdk-geo-validation

## 1.1.0

### Added

- `PostgisGeoFence` and `codehunters.geo.adapter=postgis`: zones read from an application-owned
  table or view (`codehunters.geo.postgis.table`, `.columns.*`), coverage tested on the geography
  with `ST_Covers` ([ADR 0019](../docs/adr/0019-postgis-geofence-adapter.md)). Edges are
  great-circle arcs, zones may cross the antimeridian, a border point is inside. One round trip
  tells `ZONE_NOT_FOUND` from `OUTSIDE_ZONE`; the lowest zone id wins when several cover the point.
- `codehunters.geo.postgis.query-timeout` (default `2s`), on a template of its own.

## 1.0.0

Domain-agnostic geofencing ([ADR 0015](../docs/adr/0015-domain-agnostic-geofencing.md), which
supersedes ADR 0003). Upgrade notes: [MIGRATION-1.0.md](MIGRATION-1.0.md).

### Breaking

- `RaffleGeoValidationPort.canPurchase(raffleId, point, coverageType)` is replaced by
  `GeoFenceValidator.validate(ZoneRef, GeoPoint[, Coverage])`.
- `GeoValidationDecision` is replaced by `GeoDecision` (`allowed`, `Optional<MatchedZone> zone`,
  `Optional<DenialReason> denialReason`); `MatchedZone` keeps the zone type as text.
- `CoverageType` (`NATIONAL`, `DEPARTMENTAL`, `LOCAL`) is replaced by `Coverage` (`UNRESTRICTED`,
  `RESTRICTED`). `MatchedZoneType` is removed.
- `DenialReason.OUTSIDE_ALLOWED_ZONE` becomes `OUTSIDE_ZONE`; `ZONE_NOT_FOUND` is new.
- The SQL Server adapter no longer calls a fixed procedure and is no longer registered because a
  `DataSource` exists: set `codehunters.geo.adapter=sql-server` and
  `codehunters.geo.sql-server.procedure`.
- `geo.validation.*` becomes `codehunters.geo.*`; `geo.validation.enabled` is honoured until 2.0.

### Added

- `PolygonGeoFence` over a `GeoZoneSource`: point-in-polygon with holes, borders inside,
  antimeridian edges rejected. Pure JDK.
- `SqlServerGeoFence` with a configurable procedure and parameter names (`SqlServerProcedure`).
  The procedure name is bracket-quoted in the statement.
- `codehunters.geo.sql-server.query-timeout` (default `2s`): the procedure runs on a template of
  its own with that query timeout.
- Polygon rings keep a bounding box, so distant points are rejected without walking the edges.
- Polygon rings that enclose no area (collinear vertices) are rejected.
- `GeoPoint.of(latitude, longitude)` and `ZoneRef`.

### Fixed

- An unknown zone type returned by the procedure no longer throws (`MatchedZoneType.valueOf`); it is
  reported as text.
- With several `DataSource` beans and no primary, the SQL Server adapter fails at startup with a
  clear message instead of an ambiguous-bean error.
- `Latitude` and `Longitude` reject `NaN` and infinities, without repeating the coordinate.
- The SQL Server statement starts with `SET NOCOUNT ON`.

## 0.2.0

Raffle purchase validation over `dbo.sp_geo_can_purchase_raffle` (`RaffleGeoValidationPort`).
