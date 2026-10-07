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
  identifiers at startup; values are always bound.
- Needs `spring-jdbc` and exactly one `DataSource` bean (or a primary one). Missing pieces fail at
  startup with a message naming the property.
- PostGIS or other databases: implement `GeoFenceValidator` yourself, or load the zones into a
  `GeoZoneSource`. A PostGIS adapter is a possible follow-up.

## Configuration

| Property | Default | |
|---|---|---|
| `codehunters.geo.enabled` | `true` | `false` registers nothing |
| `codehunters.geo.adapter` | — | `polygon` or `sql-server`; unset registers nothing |
| `codehunters.geo.sql-server.procedure` | — | required with `sql-server` |
| `codehunters.geo.sql-server.parameters.*` | see above | parameter names |

The 0.x key `geo.validation.enabled` is honoured as `codehunters.geo.enabled` until 2.0, with a
warning. Upgrading from 0.2.0: see [MIGRATION-1.0.md](MIGRATION-1.0.md).
