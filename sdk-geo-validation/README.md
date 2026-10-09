# sdk-geo-validation

Geofencing for any domain: does a point fall inside the zones of a **scope**? A scope is whatever
the calling service says it is (a campaign, a store, a delivery area); the SDK only sees an opaque
`ZoneRef` and never interprets it ([ADR 0015](../docs/adr/0015-domain-agnostic-geofencing.md)).

```kotlin
implementation(platform("io.codehunters:sdk-bom:<version>"))
implementation("io.codehunters:sdk-geo-validation")
```

## The port

```java
GeoDecision decision = geoFence.validate(new ZoneRef("campaign-42"), GeoPoint.of(6.2442, -75.5812));

if (decision.allowed()) {
  decision.zone().ifPresent(zone -> log.info("inside {}", zone.id()));
} else {
  decision.denialReason(); // OUTSIDE_ZONE or ZONE_NOT_FOUND
}
```

- `GeoFenceValidator.validate(scope, point)` returns a `GeoDecision`: allowed with the
  `MatchedZone` (`id`, and optionally `type`, `name`, `parent`), or denied with a `DenialReason`.
- `validate(scope, point, Coverage.UNRESTRICTED)` allows every point without a lookup
  (`GeoDecision.unrestricted()`); `Coverage.RESTRICTED` checks the zones.
- `DenialReason.OUTSIDE_ZONE`: the scope has zones and none contains the point.
  `DenialReason.ZONE_NOT_FOUND`: the scope has no zones at all.
- Zone types are free text. A source can add a new type without breaking callers; the SDK never maps
  it to an enum.
- `GeoPoint`, `Latitude` and `Longitude` reject `NaN`, infinities and out-of-range values without
  repeating the coordinate (it is the user's location). `ZoneRef` takes 1–128 characters, not blank,
  without control or format characters.
- The model and the polygon fence depend on the JDK only (ArchUnit-enforced), so they work outside
  Spring.

## Choosing an adapter

Nothing is registered until you pick one. An application `GeoFenceValidator` bean always wins.

### `polygon`: zones you load yourself

```yaml
codehunters:
  geo:
    adapter: polygon
```

Provide exactly one `GeoZoneSource` bean; `PolygonGeoFence` asks it for the scope's zones on every
check, so cache them in the source.

```java
@Bean
GeoZoneSource zones(ZoneRepository repository) {
  return scope -> repository.cachedZonesFor(scope.value()).stream()
      .map(z -> new GeoZone(MatchedZone.of(z.id(), z.type(), z.name(), null),
                            Polygon.of(z.outline()).withHole(z.lake())))
      .toList();
}
```

- Zones are tried in the order the source returns them; the first one containing the point wins.
- A point on an edge or vertex is inside (a zone owns its border). A point strictly inside a hole is
  outside; on the hole's edge it is inside.
- Edges are straight lines in degrees, not great circles: accurate for city- and region-sized zones.
- No edge may span more than 180° of longitude. Split a zone that crosses the antimeridian into two
  zones at longitude 180.
- Zones around a pole are not supported: a ring is a plane figure in degrees and cannot enclose a
  pole.
- A ring needs at least 3 distinct vertices and must enclose an area; collinear vertices are
  rejected.
- Holes are not checked to lie inside the outline, nor rings for self-intersection: validate the
  geometry where the zones are authored.
- Each ring keeps its bounding box, so points far from a zone are rejected without walking its
  edges.

### `sql-server`: a stored procedure you own

```yaml
codehunters:
  geo:
    adapter: sql-server
    sql-server:
      procedure: geo.sp_fence_check   # name or schema.name
      parameters:                     # defaults shown; names without "@"
        scope: scope_ref
        latitude: lat
        longitude: lon
        allowed: allowed
        zone-id: zone_id
        zone-type: zone_type          # optional outputs: leave blank to not pass them
        zone-name: zone_name
        zone-parent: zone_parent
```

The procedure receives the scope reference (`NVARCHAR(128)`), latitude and longitude (`FLOAT`), and
sets the OUTPUT parameters:

| Output | Type | Meaning |
|---|---|---|
| `allowed` | `BIT` | `1` inside a zone, `0` outside every zone, `NULL` when the scope has no zones |
| `zone-id` | `NVARCHAR(128)` | the matched zone; `allowed = 1` without it is treated as denied |
| `zone-type`, `zone-name`, `zone-parent` | `NVARCHAR(200)` | optional, reported as-is |

- The statement runs `SET NOCOUNT ON`, so row counts from writes inside the procedure don't reach
  the driver before the result row.
- The procedure and parameter names are written into the statement, so they are validated as
  identifiers at startup and the procedure name is bracket-quoted (`[schema].[name]`); values are
  always bound.
- The call runs on a template of its own with a query timeout (`query-timeout`, default 2 s,
  rounded up to whole seconds). A slow procedure fails the check instead of holding the request.
- Needs `spring-jdbc` and exactly one `DataSource` bean (or a primary one). Missing pieces fail at
  startup with a message naming the property.
- Other databases: implement `GeoFenceValidator` yourself, or load the zones into a
  `GeoZoneSource`. PostgreSQL with PostGIS has its own adapter, below.

### `postgis`: a zone table you own

```yaml
codehunters:
  geo:
    adapter: postgis
    postgis:
      table: geo.zones                # table or view: name or schema.name
      columns:                        # defaults shown
        scope: scope_ref
        zone-id: zone_id
        geometry: geom
        zone-type: zone_type          # optional columns: leave blank to not read them
        zone-name: zone_name
        zone-parent: zone_parent
```

Each row is one zone of one scope. A reference table:

```sql
CREATE TABLE geo.zones (
    scope_ref   VARCHAR(128) NOT NULL,          -- compared with ZoneRef.value()
    zone_id     VARCHAR(128) NOT NULL,
    zone_type   VARCHAR(200),
    zone_name   VARCHAR(200),
    zone_parent VARCHAR(200),
    geom        geography(MultiPolygon, 4326) NOT NULL
);
CREATE INDEX idx_zones_scope ON geo.zones (scope_ref);
CREATE INDEX idx_zones_geom ON geo.zones USING GIST (geom);
```

- Coverage is tested on the geography with `ST_Covers`
  ([ADR 0019](../docs/adr/0019-postgis-geofence-adapter.md)): edges are great-circle arcs, so the
  check is accurate for zones of any size, a zone may cross the antimeridian, and a point on the
  border is inside. Holes work as usual.
- The geometry column may be `geography` or `geometry` in SRID 4326 (polygon or multipolygon); a
  `geometry` is cast to `geography` on every row of the scope. Prefer `geography` with a GIST index
  for scopes with many zones; another SRID fails at query time.
- One statement tells `ZONE_NOT_FOUND` (no row for the scope) from `OUTSIDE_ZONE` (rows, none
  covering the point). When several zones cover the point, the lowest `zone-id` wins.
- The table and column names are written into the statement, so they are validated as identifiers
  at startup and double-quoted: they match case-sensitively, as PostgreSQL stores them (lower case
  for unquoted DDL). Values are always bound.
- The query runs on a template of its own with a query timeout (`query-timeout`, default 2 s,
  rounded up to whole seconds).
- Needs `spring-jdbc`, the PostgreSQL driver and exactly one `DataSource` bean (or a primary one).
  Missing pieces fail at startup with a message naming the property.
- The polygon fence draws straight lines in degrees and PostGIS great circles: near long edges the
  two adapters can disagree.

## Configuration

| Property | Default | |
|---|---|---|
| `codehunters.geo.enabled` | `true` | `false` registers nothing |
| `codehunters.geo.adapter` | — | `polygon`, `sql-server` or `postgis`; unset registers nothing |
| `codehunters.geo.sql-server.procedure` | — | required with `sql-server` |
| `codehunters.geo.sql-server.parameters.*` | see above | parameter names |
| `codehunters.geo.sql-server.query-timeout` | `2s` | longest the procedure may run; positive |
| `codehunters.geo.postgis.table` | — | required with `postgis` |
| `codehunters.geo.postgis.columns.*` | see above | column names |
| `codehunters.geo.postgis.query-timeout` | `2s` | longest the query may run; positive |

The 0.x key `geo.validation.enabled` is honoured as `codehunters.geo.enabled` until 2.0, with a
warning. Upgrading from 0.2.0: see [MIGRATION-1.0.md](MIGRATION-1.0.md).
