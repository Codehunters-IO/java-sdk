# Migrating sdk-geo-validation 1.x to 2.0

2.0.0 supports PostgreSQL only and reads configuration under `codehunters.geo.*` only.

## SQL Server is no longer supported

The `sql-server` adapter and everything around it are gone:

| Removed | Use instead |
|---|---|
| `codehunters.geo.adapter: sql-server` | `codehunters.geo.adapter: postgis` (zones in a PostGIS table) or `polygon` (zones from a `GeoZoneSource` bean) |
| `codehunters.geo.sql-server.procedure` | `codehunters.geo.postgis.table` |
| `codehunters.geo.sql-server.parameters.*` | `codehunters.geo.postgis.columns.*` |
| `codehunters.geo.sql-server.query-timeout` | `codehunters.geo.postgis.query-timeout` |
| `io.codehunters.geo.sqlserver.SqlServerGeoFence` | `io.codehunters.geo.postgis.PostgisGeoFence` |
| `io.codehunters.geo.sqlserver.SqlServerProcedure` | `io.codehunters.geo.postgis.PostgisZoneTable` |
| `GeoAdapter.SQL_SERVER` | `GeoAdapter.POSTGIS` |
| `GeoProperties.sqlServer()` | `GeoProperties.postgis()` |

A configuration that still sets `codehunters.geo.adapter: sql-server` fails at startup: the value no
longer binds to `GeoAdapter`. The PostGIS table contract and a reference DDL are in the
[README](README.md#postgis-a-zone-table-you-own). A service that must keep its zones elsewhere
implements `GeoFenceValidator` itself.

## Property renames

| Old key (no longer read) | Key to use |
|---|---|
| `geo.validation.enabled` | `codehunters.geo.enabled` |

## Removed classes

`io.codehunters.geo.config.GeoLegacyProperties` and its `spring.factories` entry.
